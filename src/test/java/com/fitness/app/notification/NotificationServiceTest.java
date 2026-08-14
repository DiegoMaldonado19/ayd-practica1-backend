package com.fitness.app.notification;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.dto.UserResponse;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.iam.model.UserStatus;
import com.fitness.app.notification.dto.NotificationStatusRequest;
import com.fitness.app.notification.model.Notification;
import com.fitness.app.notification.model.NotificationChannel;
import com.fitness.app.notification.model.NotificationStatus;
import com.fitness.app.notification.model.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What the Postman collection cannot show: that the row survives a mail server that is
 * not there, that a member with no account does not abort the caller, and that the
 * inbox belongs to the principal and to nobody else.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest
{
    private static final Long MEMBER_ID       = 3L;
    private static final Long APP_USER_ID     = 12L;
    private static final Long OTHER_USER_ID   = 99L;
    private static final Long NOTIFICATION_ID = 55L;

    @Mock private NotificationRepository notificationRepository;
    @Mock private MemberService          memberService;
    @Mock private JavaMailSender         mailSender;

    @InjectMocks private NotificationService notificationService;

    @Test
    void anInAppNoticeIsBornSentWithoutTouchingTheMailServer()
    {
        when(memberService.accountOf(MEMBER_ID)).thenReturn(Optional.of(account("socio@correo.com")));

        notificationService.membershipFrozen(MEMBER_ID, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 10));

        var saved = captureSaved();

        assertEquals(NotificationType.MEMBERSHIP_FROZEN, saved.getNotificationType());
        assertEquals(NotificationChannel.IN_APP, saved.getChannel());
        assertEquals(NotificationStatus.SENT, saved.getStatus());
        assertNotNull(saved.getSentAt());
        verifyNoInteractions(mailSender);
    }

    @Test
    void aMailThatCannotBeDeliveredLeavesTheRowFailedWithItsReason()
    {
        // Without SMTP the flow still has to be demonstrable: the notice stays in the
        // inbox and says why it never left.
        when(memberService.accountOf(MEMBER_ID)).thenReturn(Optional.of(account("socio@correo.com")));
        doThrow(new MailSendException("Conexión rechazada")).when(mailSender).send(any(SimpleMailMessage.class));

        notificationService.membershipExpired(MEMBER_ID, LocalDate.of(2026, 8, 9));

        var saved = captureSaved();

        assertEquals(NotificationChannel.EMAIL, saved.getChannel());
        assertEquals(NotificationStatus.FAILED, saved.getStatus());
        assertNull(saved.getSentAt());
        assertNotNull(saved.getFailureReason());
    }

    @Test
    void aMemberWithNoAccountIsSkippedInsteadOfBreakingTheSweep()
    {
        // The 00:05 task walks every expired contract: one member without credentials
        // must not take the rest of the sweep down with it.
        when(memberService.accountOf(MEMBER_ID)).thenReturn(Optional.empty());

        notificationService.membershipExpiring(MEMBER_ID, LocalDate.of(2026, 8, 14), 5);

        verify(notificationRepository, never()).save(any());
        verifyNoInteractions(mailSender);
    }

    @Test
    void anAccountWithoutAnEmailFailsTheRowInsteadOfCallingTheMailServer()
    {
        when(memberService.accountOf(MEMBER_ID)).thenReturn(Optional.of(account(null)));

        notificationService.membershipExpired(MEMBER_ID, LocalDate.of(2026, 8, 9));

        assertEquals(NotificationStatus.FAILED, captureSaved().getStatus());
        verifyNoInteractions(mailSender);
    }

    @Test
    void readingSealsTheInstantOnlyTheFirstTime()
    {
        var notification = inboxRow(APP_USER_ID);

        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(notification));

        var response = notificationService.changeStatus(NOTIFICATION_ID, read(), principal(APP_USER_ID));
        var readAt   = response.readAt();

        assertEquals(NotificationStatus.READ, response.status());
        assertNotNull(readAt);
        assertEquals(readAt, notificationService.changeStatus(NOTIFICATION_ID, read(), principal(APP_USER_ID)).readAt());
    }

    @Test
    void refusesToMarkTheNoticeOfAnotherAccount()
    {
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(inboxRow(OTHER_USER_ID)));

        assertEquals(ErrorCode.FORBIDDEN_RESOURCE,
                     assertThrows(BusinessException.class,
                                  () -> notificationService.changeStatus(NOTIFICATION_ID, read(), principal(APP_USER_ID)))
                             .getErrorCode());
    }

    @Test
    void refusesAnyDestinationOtherThanRead()
    {
        // PENDING, SENT and FAILED are delivery states only the server writes.
        when(notificationRepository.findById(NOTIFICATION_ID)).thenReturn(Optional.of(inboxRow(APP_USER_ID)));

        var request = new NotificationStatusRequest(NotificationStatus.SENT);

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION,
                     assertThrows(BusinessException.class,
                                  () -> notificationService.changeStatus(NOTIFICATION_ID, request, principal(APP_USER_ID)))
                             .getErrorCode());
    }

    private Notification captureSaved()
    {
        var captor = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        return captor.getValue();
    }

    private static Notification inboxRow(Long appUserId)
    {
        var notification = new Notification();

        notification.setNotificationId(NOTIFICATION_ID);
        notification.setAppUserId(appUserId);
        notification.setNotificationType(NotificationType.MEMBERSHIP_EXPIRING);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.SENT);

        return notification;
    }

    private static NotificationStatusRequest read()
    {
        return new NotificationStatusRequest(NotificationStatus.READ);
    }

    private static UserResponse account(String email)
    {
        return new UserResponse(APP_USER_ID, 7L, "socio", "Ana Pérez", email,
                                UserRole.MEMBER, UserStatus.ACTIVE, false, null, null, null);
    }

    private static AuthenticatedUser principal(Long appUserId)
    {
        return new AuthenticatedUser(appUserId, "socio", UserRole.MEMBER);
    }
}
