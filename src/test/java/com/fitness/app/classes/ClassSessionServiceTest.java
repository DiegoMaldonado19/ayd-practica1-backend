package com.fitness.app.classes;

import com.fitness.app.classes.dto.SessionCancellationRequest;
import com.fitness.app.classes.dto.SessionRescheduleRequest;
import com.fitness.app.classes.dto.SessionStatusRequest;
import com.fitness.app.classes.model.ClassEnrollment;
import com.fitness.app.classes.model.ClassSession;
import com.fitness.app.classes.model.EnrollmentStatus;
import com.fitness.app.classes.model.GroupClass;
import com.fitness.app.classes.model.SessionStatus;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.TrainerService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The session state machine and the reschedule conflict. Both are service rules with
 * no constraint behind them: ck_session_status accepts any value of the enum, and
 * uq_group_class_slot only guards the recurring class, never a single date.
 */
@ExtendWith(MockitoExtension.class)
class ClassSessionServiceTest
{
    private static final Long SESSION_ID = 40L;
    private static final Long TRAINER_ID = 7L;

    @Mock private ClassSessionRepository    classSessionRepository;
    @Mock private ClassEnrollmentRepository classEnrollmentRepository;
    @Mock private WaitlistEntryRepository   waitlistEntryRepository;
    @Mock private GroupClassService         groupClassService;
    @Mock private TrainerService            trainerService;
    @Mock private NotificationService       notificationService;

    @InjectMocks private ClassSessionService classSessionService;

    @Test
    void opensAScheduledSessionAndStampsTheOpeningInstant()
    {
        var session = session(SessionStatus.SCHEDULED);

        when(classSessionRepository.findDetailById(SESSION_ID)).thenReturn(Optional.of(session));

        var response = classSessionService.changeStatus(SESSION_ID, new SessionStatusRequest(SessionStatus.IN_PROGRESS),
                                                        admin());

        assertEquals(SessionStatus.IN_PROGRESS, response.status());
        assertNotNull(session.getOpenedAt());
    }

    @Test
    void refusesToCloseASessionThatWasNeverOpened()
    {
        // ck_session_closed says the same at the database level: closed_at demands opened_at.
        when(classSessionRepository.findDetailById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.SCHEDULED)));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION,
                     assertThrows(BusinessException.class,
                                  () -> classSessionService.changeStatus(SESSION_ID,
                                                                         new SessionStatusRequest(SessionStatus.COMPLETED),
                                                                         admin()))
                             .getErrorCode());
    }

    @Test
    void refusesToOpenASessionTwice()
    {
        when(classSessionRepository.findDetailById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.IN_PROGRESS)));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION,
                     assertThrows(BusinessException.class,
                                  () -> classSessionService.changeStatus(SESSION_ID,
                                                                         new SessionStatusRequest(SessionStatus.IN_PROGRESS),
                                                                         admin()))
                             .getErrorCode());
    }

    @Test
    void rescheduleRefusesATrainerAlreadyBookedAtThatDateAndTime()
    {
        var request = new SessionRescheduleRequest(LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), (short) 20, TRAINER_ID);

        when(classSessionRepository.findDetailById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.SCHEDULED)));
        when(classSessionRepository.existsByTrainerIdAndSessionDateAndStartTimeAndClassSessionIdNot(
                TRAINER_ID, request.sessionDate(), request.startTime(), SESSION_ID)).thenReturn(true);

        assertEquals(ErrorCode.TRAINER_SCHEDULE_CONFLICT,
                     assertThrows(BusinessException.class,
                                  () -> classSessionService.reschedule(SESSION_ID, request)).getErrorCode());
    }

    @Test
    void rescheduleRefusesACancelledSession()
    {
        var request = new SessionRescheduleRequest(LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), (short) 20, TRAINER_ID);

        when(classSessionRepository.findDetailById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.CANCELLED)));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION,
                     assertThrows(BusinessException.class,
                                  () -> classSessionService.reschedule(SESSION_ID, request)).getErrorCode());
    }

    @Test
    void refusesATrainerActingOnSomebodyElsesSession()
    {
        when(classSessionRepository.findDetailById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.SCHEDULED)));
        when(trainerService.findTrainerIdByUser(trainer())).thenReturn(99L);

        assertEquals(ErrorCode.TRAINER_SCOPE_VIOLATION,
                     assertThrows(BusinessException.class,
                                  () -> classSessionService.changeStatus(SESSION_ID,
                                                                         new SessionStatusRequest(SessionStatus.IN_PROGRESS),
                                                                         trainer()))
                             .getErrorCode());
    }

    @Test
    void cancellingASessionCancelsTheEnrolmentsAndNotifiesTheirMembers()
    {
        // "Cancela la sesión con motivo y notifica a los inscritos" (§3.6): the notice
        // goes to whoever still held a seat, with the reason the administrator gave.
        var enrollment = enrollment(11L);

        when(classSessionRepository.findDetailById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.SCHEDULED)));
        when(classEnrollmentRepository.findByClassSessionIdAndStatus(SESSION_ID, EnrollmentStatus.ENROLLED))
                .thenReturn(List.of(enrollment));

        classSessionService.cancel(SESSION_ID, new SessionCancellationRequest("Entrenador incapacitado"));

        assertEquals(EnrollmentStatus.CANCELLED, enrollment.getStatus());
        verify(notificationService).sessionCancelled(List.of(11L), "Yoga matutino", LocalDate.of(2026, 9, 8),
                                                     "Entrenador incapacitado");
    }

    @Test
    void reschedulingNotifiesTheEnrolledMembersOfTheNewSlot()
    {
        var request = new SessionRescheduleRequest(LocalDate.of(2026, 9, 15), LocalTime.of(18, 0), (short) 20, TRAINER_ID);

        when(classSessionRepository.findDetailById(SESSION_ID)).thenReturn(Optional.of(session(SessionStatus.SCHEDULED)));
        when(classSessionRepository.existsByTrainerIdAndSessionDateAndStartTimeAndClassSessionIdNot(
                TRAINER_ID, request.sessionDate(), request.startTime(), SESSION_ID)).thenReturn(false);
        when(classEnrollmentRepository.findByClassSessionIdAndStatus(SESSION_ID, EnrollmentStatus.ENROLLED))
                .thenReturn(List.of(enrollment(11L)));

        classSessionService.reschedule(SESSION_ID, request);

        // The new slot, not the old one: the member is told where the class moved to.
        verify(notificationService).sessionRescheduled(List.of(11L), "Yoga matutino", LocalDate.of(2026, 9, 15),
                                                       LocalTime.of(18, 0));
    }

    private static ClassEnrollment enrollment(Long memberId)
    {
        var enrollment = new ClassEnrollment();

        enrollment.setClassEnrollmentId(70L);
        enrollment.setClassSessionId(SESSION_ID);
        enrollment.setMemberId(memberId);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);

        return enrollment;
    }

    private static ClassSession session(SessionStatus status)
    {
        var groupClass = new GroupClass();

        groupClass.setGroupClassId(1L);
        groupClass.setName("Yoga matutino");
        groupClass.setDurationMinutes((short) 60);

        var session = new ClassSession();

        session.setClassSessionId(SESSION_ID);
        session.setGroupClass(groupClass);
        session.setTrainerId(TRAINER_ID);
        session.setSessionDate(LocalDate.of(2026, 9, 8));
        session.setStartTime(LocalTime.of(7, 0));
        session.setMaxCapacity((short) 20);
        session.setStatus(status);

        return session;
    }

    private static AuthenticatedUser admin()
    {
        return new AuthenticatedUser(1L, "admin", UserRole.ADMIN);
    }

    private static AuthenticatedUser trainer()
    {
        return new AuthenticatedUser(2L, "entrenador", UserRole.TRAINER);
    }
}
