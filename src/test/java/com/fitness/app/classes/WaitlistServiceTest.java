package com.fitness.app.classes;

import com.fitness.app.classes.dto.WaitlistRequest;
import com.fitness.app.classes.model.ClassSession;
import com.fitness.app.classes.model.GroupClass;
import com.fitness.app.classes.model.SessionStatus;
import com.fitness.app.classes.model.WaitlistEntry;
import com.fitness.app.classes.model.WaitlistStatus;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.model.PlanBenefit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The confirmation window and who may join the queue. Both are service rules: the
 * database only knows that a NOTIFIED entry carries a notified_at.
 */
@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest
{
    private static final Long SESSION_ID = 40L;
    private static final Long MEMBER_ID  = 5L;
    private static final Long ENTRY_ID   = 90L;

    @Mock private WaitlistEntryRepository waitlistEntryRepository;
    @Mock private ClassSessionService     classSessionService;
    @Mock private MemberService           memberService;
    @Mock private MembershipService       membershipService;
    @Mock private EnrollmentService       enrollmentService;

    @InjectMocks private WaitlistService waitlistService;

    @Test
    void confirmingInsideTheWindowPromotesTheEntryAndCreatesTheEnrolment()
    {
        var entry = notifiedEntry(Instant.now().plusSeconds(600));

        when(waitlistEntryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));

        waitlistService.confirm(ENTRY_ID, principal());

        assertEquals(WaitlistStatus.PROMOTED, entry.getStatus());
        verify(enrollmentService).confirmFromWaitlist(SESSION_ID, MEMBER_ID, principal());
    }

    @Test
    void refusesAConfirmationPastItsDeadline()
    {
        var entry = notifiedEntry(Instant.now().minusSeconds(60));

        when(waitlistEntryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));

        assertEquals(ErrorCode.WAITLIST_CONFIRMATION_EXPIRED,
                     assertThrows(BusinessException.class,
                                  () -> waitlistService.confirm(ENTRY_ID, principal())).getErrorCode());
    }

    @Test
    void refusesToConfirmAnEntryThatWasNeverNotified()
    {
        var entry = notifiedEntry(Instant.now().plusSeconds(600));

        entry.setStatus(WaitlistStatus.WAITING);

        when(waitlistEntryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));

        assertEquals(ErrorCode.WAITLIST_CONFIRMATION_EXPIRED,
                     assertThrows(BusinessException.class,
                                  () -> waitlistService.confirm(ENTRY_ID, principal())).getErrorCode());
    }

    @Test
    void leavingTheQueueAfterBeingNotifiedFreesTheReservedSeat()
    {
        var entry = notifiedEntry(Instant.now().plusSeconds(600));

        when(waitlistEntryRepository.findById(ENTRY_ID)).thenReturn(Optional.of(entry));

        waitlistService.leave(ENTRY_ID, principal());

        assertEquals(WaitlistStatus.CANCELLED, entry.getStatus());
        verify(enrollmentService).promoteNext(SESSION_ID);
    }

    @Test
    void refusesToQueueOnASessionThatStillHasSeats()
    {
        // "Se une a la lista de espera solo si la clase está llena": with a free seat
        // the member is supposed to enrol, not queue.
        var session = session();

        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session);
        when(membershipService.hasBenefit(MEMBER_ID, PlanBenefit.GROUP_CLASSES)).thenReturn(true);
        when(classSessionService.seatsTaken(SESSION_ID)).thenReturn(1);

        assertEquals(ErrorCode.VALIDATION_ERROR,
                     assertThrows(BusinessException.class,
                                  () -> waitlistService.join(SESSION_ID, new WaitlistRequest(MEMBER_ID), principal()))
                             .getErrorCode());
    }

    @Test
    void refusesToQueueAMemberAlreadyEnrolled()
    {
        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session());
        when(membershipService.hasBenefit(MEMBER_ID, PlanBenefit.GROUP_CLASSES)).thenReturn(true);
        when(enrollmentService.isActivelyEnrolled(SESSION_ID, MEMBER_ID)).thenReturn(true);

        assertEquals(ErrorCode.ALREADY_ENROLLED,
                     assertThrows(BusinessException.class,
                                  () -> waitlistService.join(SESSION_ID, new WaitlistRequest(MEMBER_ID), principal()))
                             .getErrorCode());
    }

    private static ClassSession session()
    {
        var groupClass = new GroupClass();

        groupClass.setGroupClassId(1L);
        groupClass.setName("Yoga matutino");
        groupClass.setDurationMinutes((short) 60);

        var session = new ClassSession();

        session.setClassSessionId(SESSION_ID);
        session.setGroupClass(groupClass);
        session.setTrainerId(7L);
        session.setSessionDate(LocalDate.of(2026, 9, 8));
        session.setStartTime(LocalTime.of(7, 0));
        session.setMaxCapacity((short) 20);
        session.setStatus(SessionStatus.SCHEDULED);

        return session;
    }

    private static WaitlistEntry notifiedEntry(Instant deadline)
    {
        var entry = new WaitlistEntry();

        entry.setWaitlistEntryId(ENTRY_ID);
        entry.setClassSessionId(SESSION_ID);
        entry.setMemberId(MEMBER_ID);
        entry.setStatus(WaitlistStatus.NOTIFIED);
        entry.setRequestedAt(Instant.now().minusSeconds(3600));
        entry.setNotifiedAt(Instant.now().minusSeconds(120));
        entry.setConfirmationDeadline(deadline);

        return entry;
    }

    private static AuthenticatedUser principal()
    {
        return new AuthenticatedUser(1L, "socio", UserRole.MEMBER);
    }
}
