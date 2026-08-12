package com.fitness.app.classes;

import com.fitness.app.classes.dto.AttendanceRequest;
import com.fitness.app.classes.dto.EnrollmentRequest;
import com.fitness.app.classes.model.ClassEnrollment;
import com.fitness.app.classes.model.ClassSession;
import com.fitness.app.classes.model.EnrollmentStatus;
import com.fitness.app.classes.model.GroupClass;
import com.fitness.app.classes.model.SessionStatus;
import com.fitness.app.classes.model.WaitlistEntry;
import com.fitness.app.classes.model.WaitlistStatus;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.config.GymProperties;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.model.MembershipPlan;
import com.fitness.app.membership.model.PlanBenefit;
import com.fitness.app.notification.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The rules the Postman collection cannot show on its own: the order in which the
 * seven enrolment validations fire, that a cancelled row is reactivated instead of
 * duplicated, the anticipation margin, and that the Elite member jumps the queue.
 */
@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest
{
    private static final Long SESSION_ID    = 40L;
    private static final Long MEMBER_ID     = 5L;
    private static final Long ELITE_MEMBER  = 6L;
    private static final Long ENROLLMENT_ID = 70L;

    @Mock private ClassEnrollmentRepository classEnrollmentRepository;
    @Mock private WaitlistEntryRepository   waitlistEntryRepository;
    @Mock private ClassSessionService       classSessionService;
    @Mock private MemberService             memberService;
    @Mock private MembershipService         membershipService;
    @Mock private NotificationService       notificationService;

    // The real values of application.yml: 2 hours of margin, 60 minutes to confirm.
    @Spy private GymProperties gymProperties = new GymProperties(
            new GymProperties.Freeze(15, 2, 90),
            new GymProperties.GuestPass(1),
            new GymProperties.Classes(2, 60),
            new GymProperties.Membership(5),
        new GymProperties.Nutrition(10));

    @InjectMocks private EnrollmentService enrollmentService;

    @Test
    void refusesAPlanWithoutTheGroupClassesBenefit()
    {
        // Step 4 of the order: the benefit is checked before the seat, so a Basic member
        // is told to upgrade instead of being told the class is full.
        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session());
        when(membershipService.hasBenefit(MEMBER_ID, PlanBenefit.GROUP_CLASSES)).thenReturn(false);

        assertEquals(ErrorCode.PLAN_BENEFIT_NOT_INCLUDED, enrollFails());
    }

    @Test
    void refusesASecondEnrolmentInTheSameSession()
    {
        var enrolled = enrollment(EnrollmentStatus.ENROLLED);

        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session());
        when(membershipService.hasBenefit(MEMBER_ID, PlanBenefit.GROUP_CLASSES)).thenReturn(true);
        when(classEnrollmentRepository.findByClassSessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(enrolled));

        assertEquals(ErrorCode.ALREADY_ENROLLED, enrollFails());
    }

    @Test
    void stopsAtTheWeeklyLimitOfThePlan()
    {
        // "No permitir una cuarta inscripción en la misma semana si el límite es de 3."
        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session());
        when(membershipService.hasBenefit(MEMBER_ID, PlanBenefit.GROUP_CLASSES)).thenReturn(true);
        when(membershipService.weeklyClassLimit(MEMBER_ID)).thenReturn(3);
        when(classEnrollmentRepository.countWeeklyActive(anyLong(), any(), any(), any())).thenReturn(3L);

        assertEquals(ErrorCode.WEEKLY_LIMIT_REACHED, enrollFails());
    }

    @Test
    void countsTheWeeklyLimitOverTheIsoWeekOfTheSession()
    {
        // The session is a Tuesday: the window is that Monday to that Sunday, never
        // the week of today - enrolling on Sunday spends the following week.
        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session());
        when(membershipService.hasBenefit(MEMBER_ID, PlanBenefit.GROUP_CLASSES)).thenReturn(true);
        when(membershipService.weeklyClassLimit(MEMBER_ID)).thenReturn(3);
        when(classEnrollmentRepository.countWeeklyActive(MEMBER_ID, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13),
                                                         EnrollmentStatus.CANCELLED)).thenReturn(3L);

        assertEquals(ErrorCode.WEEKLY_LIMIT_REACHED, enrollFails());
    }

    @Test
    void answersSeatUnavailableWhenTheSessionIsFull()
    {
        var session = session();

        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session);
        when(membershipService.hasBenefit(MEMBER_ID, PlanBenefit.GROUP_CLASSES)).thenReturn(true);
        when(membershipService.weeklyClassLimit(MEMBER_ID)).thenReturn(MembershipPlan.UNLIMITED_CLASSES);
        when(classSessionService.seatsTaken(SESSION_ID)).thenReturn((int) session.getMaxCapacity());

        assertEquals(ErrorCode.SEAT_UNAVAILABLE, enrollFails());
    }

    @Test
    void reactivatesACancelledEnrolmentInsteadOfInsertingASecondOne()
    {
        // uq_enroll_member carries no partial predicate: a second row would collide.
        var cancelled = enrollment(EnrollmentStatus.CANCELLED);

        cancelled.setCancelledAt(Instant.now());

        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session());
        when(membershipService.hasBenefit(MEMBER_ID, PlanBenefit.GROUP_CLASSES)).thenReturn(true);
        when(membershipService.weeklyClassLimit(MEMBER_ID)).thenReturn(MembershipPlan.UNLIMITED_CLASSES);
        when(classEnrollmentRepository.findByClassSessionIdAndMemberId(SESSION_ID, MEMBER_ID))
                .thenReturn(Optional.of(cancelled));
        when(classEnrollmentRepository.save(any(ClassEnrollment.class))).thenAnswer(call -> call.getArgument(0));

        var response = enrollmentService.enroll(SESSION_ID, new EnrollmentRequest(MEMBER_ID, null), principal());

        assertEquals(ENROLLMENT_ID, response.classEnrollmentId());
        assertEquals(EnrollmentStatus.ENROLLED, response.status());
        assertNull(response.cancelledAt());
    }

    @Test
    void refusesToCancelOutsideTheAnticipationMargin()
    {
        var session    = session();
        var enrollment = enrollment(EnrollmentStatus.ENROLLED);

        // One hour from now, with a two-hour margin configured.
        session.setSessionDate(LocalDate.now());
        session.setStartTime(LocalTime.now().plusHours(1));

        when(classEnrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session);

        assertEquals(ErrorCode.CANCELLATION_WINDOW_CLOSED,
                     assertThrows(BusinessException.class,
                                  () -> enrollmentService.cancel(ENROLLMENT_ID, principal())).getErrorCode());
    }

    @Test
    void cancellingInsideTheMarginNotifiesTheEliteMemberFirst()
    {
        // "Dándole prioridad a los socios con Plan Élite": the Premium member asked
        // first, the Elite member is the one who gets the seat.
        var enrollment = enrollment(EnrollmentStatus.ENROLLED);
        var premium    = waitingEntry(MEMBER_ID, Instant.now().minusSeconds(600));
        var elite      = waitingEntry(ELITE_MEMBER, Instant.now());

        when(classEnrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));
        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session());
        when(waitlistEntryRepository.findByClassSessionIdAndStatusOrderByRequestedAtAsc(SESSION_ID, WaitlistStatus.WAITING))
                .thenReturn(List.of(premium, elite));
        when(membershipService.findActiveTiers(anyCollection()))
                .thenReturn(Map.of(MEMBER_ID, (short) 2, ELITE_MEMBER, (short) 3));

        enrollmentService.cancel(ENROLLMENT_ID, principal());

        assertEquals(EnrollmentStatus.CANCELLED, enrollment.getStatus());
        assertEquals(WaitlistStatus.NOTIFIED, elite.getStatus());
        assertEquals(WaitlistStatus.WAITING, premium.getStatus());
        // "Notificar automáticamente al primer socio en la lista de espera" (Enunciado):
        // the seat is not just reserved, the member is told about it.
        verify(notificationService).waitlistSeatAvailable(eq(ELITE_MEMBER), eq("Yoga matutino"), any(), any());
    }

    @Test
    void expiresAStaleReservationAndNotifiesTheNextInLine()
    {
        // The lazy refresh of design decision #3: no third @Scheduled task.
        var stale = waitingEntry(MEMBER_ID, Instant.now().minusSeconds(7200));
        var next  = waitingEntry(ELITE_MEMBER, Instant.now());

        stale.setStatus(WaitlistStatus.NOTIFIED);
        stale.setConfirmationDeadline(Instant.now().minusSeconds(60));

        when(waitlistEntryRepository.findByClassSessionIdAndStatusAndConfirmationDeadlineBefore(
                anyLong(), any(), any())).thenReturn(List.of(stale));
        when(waitlistEntryRepository.findByClassSessionIdAndStatusOrderByRequestedAtAsc(SESSION_ID, WaitlistStatus.WAITING))
                .thenReturn(List.of(next));
        when(membershipService.findActiveTiers(anyCollection())).thenReturn(Map.of(ELITE_MEMBER, (short) 3));
        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session());

        enrollmentService.refreshWaitlist(SESSION_ID);

        assertEquals(WaitlistStatus.EXPIRED, stale.getStatus());
        assertEquals(WaitlistStatus.NOTIFIED, next.getStatus());
        verify(notificationService).waitlistSeatAvailable(eq(ELITE_MEMBER), eq("Yoga matutino"), any(), any());
    }

    @Test
    void refusesToMarkAttendanceOnACancelledEnrolment()
    {
        // ck_enroll_cancel is an if-and-only-if: ATTENDED with cancelled_at still set
        // would be a constraint violation and a 500 instead of a readable answer.
        var session    = session();
        var enrollment = enrollment(EnrollmentStatus.CANCELLED);

        session.setStatus(SessionStatus.IN_PROGRESS);
        enrollment.setCancelledAt(Instant.now());

        when(classSessionService.findOrFail(SESSION_ID)).thenReturn(session);
        when(classEnrollmentRepository.findById(ENROLLMENT_ID)).thenReturn(Optional.of(enrollment));

        var request = new AttendanceRequest(List.of(
                new AttendanceRequest.AttendanceMark(ENROLLMENT_ID, EnrollmentStatus.ATTENDED)));

        assertEquals(ErrorCode.VALIDATION_ERROR,
                     assertThrows(BusinessException.class,
                                  () -> enrollmentService.markAttendance(SESSION_ID, request, principal()))
                             .getErrorCode());
    }

    private ErrorCode enrollFails()
    {
        return assertThrows(BusinessException.class,
                            () -> enrollmentService.enroll(SESSION_ID, new EnrollmentRequest(MEMBER_ID, null), principal()))
                .getErrorCode();
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
        // A Tuesday, so the ISO week of the session runs 2026-09-07 to 2026-09-13.
        session.setSessionDate(LocalDate.of(2026, 9, 8));
        session.setStartTime(LocalTime.of(7, 0));
        session.setMaxCapacity((short) 20);
        session.setStatus(SessionStatus.SCHEDULED);

        return session;
    }

    private static ClassEnrollment enrollment(EnrollmentStatus status)
    {
        var enrollment = new ClassEnrollment();

        enrollment.setClassEnrollmentId(ENROLLMENT_ID);
        enrollment.setClassSessionId(SESSION_ID);
        enrollment.setMemberId(MEMBER_ID);
        enrollment.setStatus(status);
        enrollment.setEnrolledAt(Instant.now());
        enrollment.setEnrolledByUserId(1L);

        return enrollment;
    }

    private static WaitlistEntry waitingEntry(Long memberId, Instant requestedAt)
    {
        var entry = new WaitlistEntry();

        entry.setWaitlistEntryId(memberId);
        entry.setClassSessionId(SESSION_ID);
        entry.setMemberId(memberId);
        entry.setStatus(WaitlistStatus.WAITING);
        entry.setRequestedAt(requestedAt);

        return entry;
    }

    private static AuthenticatedUser principal()
    {
        return new AuthenticatedUser(1L, "recepcion", UserRole.RECEPTIONIST);
    }
}
