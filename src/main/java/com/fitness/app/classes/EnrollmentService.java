package com.fitness.app.classes;

import com.fitness.app.access.model.AccessChannel;
import com.fitness.app.classes.dto.AttendanceRequest;
import com.fitness.app.classes.dto.ClassEnrollmentResponse;
import com.fitness.app.classes.dto.EnrollmentRequest;
import com.fitness.app.classes.model.ClassEnrollment;
import com.fitness.app.classes.model.EnrollmentStatus;
import com.fitness.app.classes.model.SessionStatus;
import com.fitness.app.classes.model.WaitlistEntry;
import com.fitness.app.classes.model.WaitlistStatus;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.config.GymProperties;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.model.MembershipPlan;
import com.fitness.app.membership.model.PlanBenefit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * Inscribir, cancelar, roster, historial del socio, asistencia y promover la cola: el
 * corazón de negocio del módulo (02-Modulos §2.6).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EnrollmentService
{
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final WaitlistEntryRepository   waitlistEntryRepository;
    private final ClassSessionService       classSessionService;
    private final MemberService             memberService;
    private final MembershipService         membershipService;
    private final GymProperties             gymProperties;

    /** The seven-step order of 02-Modulos §2.6, fixed. */
    public ClassEnrollmentResponse enroll(Long sessionId, EnrollmentRequest request, AuthenticatedUser principal)
    {
        refreshWaitlist(sessionId);

        var session = classSessionService.findOrFail(sessionId);

        if (session.getStatus() == SessionStatus.CANCELLED)
        {
            throw new BusinessException(ErrorCode.SESSION_CANCELLED);
        }

        if (session.getStatus() != SessionStatus.SCHEDULED)
        {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        memberService.findById(request.memberId(), principal);

        // Throws MEMBERSHIP_NOT_ACTIVE / MEMBERSHIP_FROZEN / MEMBERSHIP_EXPIRED / MEMBERSHIP_CANCELLED.
        membershipService.findActiveMembership(request.memberId());

        if (!membershipService.hasBenefit(request.memberId(), PlanBenefit.GROUP_CLASSES))
        {
            throw new BusinessException(ErrorCode.PLAN_BENEFIT_NOT_INCLUDED);
        }

        var existing = classEnrollmentRepository.findByClassSessionIdAndMemberId(sessionId, request.memberId());

        if (existing.isPresent() && existing.get().getStatus() != EnrollmentStatus.CANCELLED)
        {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED);
        }

        assertWeeklyLimit(request.memberId(), session.getSessionDate());

        if (classSessionService.seatsTaken(sessionId) >= session.getMaxCapacity())
        {
            throw new BusinessException(ErrorCode.SEAT_UNAVAILABLE);
        }

        return enrollOrReactivate(sessionId, request.memberId(),
                                  request.channel() == null ? AccessChannel.SELF_SERVICE : request.channel(), principal);
    }

    /**
     * "Cancela la inscripción. Si está dentro del margen de anticipación, libera el
     * cupo y promueve al primero de la lista de espera" (§3.6).
     */
    public void cancel(Long enrollmentId, AuthenticatedUser principal)
    {
        var enrollment = findOrFail(enrollmentId);

        memberService.findById(enrollment.getMemberId(), principal);

        if (enrollment.getStatus() != EnrollmentStatus.ENROLLED)
        {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        refreshWaitlist(enrollment.getClassSessionId());

        var session = classSessionService.findOrFail(enrollment.getClassSessionId());
        var margin  = session.startsAt().minusHours(gymProperties.classes().cancellationWindowHours());

        if (LocalDateTime.now().isAfter(margin))
        {
            throw new BusinessException(ErrorCode.CANCELLATION_WINDOW_CLOSED);
        }

        enrollment.setStatus(EnrollmentStatus.CANCELLED);
        enrollment.setCancelledAt(Instant.now());

        promoteNext(enrollment.getClassSessionId());
    }

    /** "Lista de inscritos de la sesión" (§3.6). */
    @Transactional(readOnly = true)
    public Page<ClassEnrollmentResponse> roster(Long sessionId, Pageable pageable)
    {
        classSessionService.findOrFail(sessionId);

        return classEnrollmentRepository.findByClassSessionId(sessionId, pageable).map(ClassEnrollmentResponse::from);
    }

    /** "Clases del socio. Filtros: from, to, status" (§3.2). */
    @Transactional(readOnly = true)
    public Page<ClassEnrollmentResponse> history(Long memberId, LocalDate from, LocalDate to, EnrollmentStatus status,
                                                 AuthenticatedUser principal, Pageable pageable)
    {
        memberService.findById(memberId, principal);

        var range = DateRange.parse(from, to);

        return classEnrollmentRepository.findHistory(memberId, range.from(), range.to(), status, pageable)
                .map(ClassEnrollmentResponse::from);
    }

    /** "Marca la asistencia de toda la lista en una sola operación" (§3.6). */
    public List<ClassEnrollmentResponse> markAttendance(Long sessionId, AttendanceRequest request,
                                                        AuthenticatedUser principal)
    {
        var session = classSessionService.findOrFail(sessionId);

        classSessionService.assertTrainerScope(session, principal);

        if (session.getStatus() != SessionStatus.IN_PROGRESS && session.getStatus() != SessionStatus.COMPLETED)
        {
            throw new BusinessException(ErrorCode.SESSION_NOT_OPEN);
        }

        var now     = Instant.now();
        var updated = new ArrayList<ClassEnrollmentResponse>();

        for (var mark : request.attendances())
        {
            if (mark.status() != EnrollmentStatus.ATTENDED && mark.status() != EnrollmentStatus.ABSENT)
            {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La asistencia solo admite ATTENDED o ABSENT.");
            }

            var enrollment = findOrFail(mark.enrollmentId());

            if (!enrollment.getClassSessionId().equals(sessionId))
            {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La inscripción no pertenece a esta sesión.");
            }

            // ck_enroll_cancel is an if-and-only-if: moving a CANCELLED row out of that
            // status while cancelled_at stays set violates the constraint.
            if (enrollment.getStatus() == EnrollmentStatus.CANCELLED)
            {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La inscripción está cancelada.");
            }

            enrollment.setStatus(mark.status());
            enrollment.setAttendanceMarkedAt(now);

            updated.add(ClassEnrollmentResponse.from(enrollment));
        }

        return updated;
    }

    /** Whether the member already holds a non-cancelled enrolment in this session, for WaitlistService.join. */
    boolean isActivelyEnrolled(Long sessionId, Long memberId)
    {
        return classEnrollmentRepository.findByClassSessionIdAndMemberId(sessionId, memberId)
                .map(enrollment -> enrollment.getStatus() != EnrollmentStatus.CANCELLED)
                .orElse(false);
    }

    /**
     * Creates (or reactivates) the enrolment a confirmed waitlist seat becomes.
     * WaitlistService.confirm already checked the reservation is NOTIFIED and still
     * within its deadline, so this skips enroll()'s membership/benefit/limit/seat
     * checks on purpose: they were the reason this member got a reserved seat in the
     * first place, and re-running them would defeat the reservation.
     */
    ClassEnrollmentResponse confirmFromWaitlist(Long sessionId, Long memberId, AuthenticatedUser principal)
    {
        return enrollOrReactivate(sessionId, memberId, AccessChannel.SELF_SERVICE, principal);
    }

    /** uq_enroll_member keeps one row per (session, member): a CANCELLED one is revived, never duplicated. */
    private ClassEnrollmentResponse enrollOrReactivate(Long sessionId, Long memberId, AccessChannel channel,
                                                       AuthenticatedUser principal)
    {
        var enrollment = classEnrollmentRepository.findByClassSessionIdAndMemberId(sessionId, memberId)
                .orElseGet(ClassEnrollment::new);

        enrollment.setClassSessionId(sessionId);
        enrollment.setMemberId(memberId);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        enrollment.setChannel(channel);
        enrollment.setEnrolledAt(Instant.now());
        enrollment.setCancelledAt(null);
        enrollment.setEnrolledByUserId(principal.appUserId());

        return ClassEnrollmentResponse.from(classEnrollmentRepository.save(enrollment));
    }

    /**
     * "El socio no puede [...] inscribirse a clases [...] hasta que reactive su
     * membresía": called by membership.MembershipService on freeze and on expiry, in
     * the direct-call direction 02-Modulos §3 documents. Public because the caller is
     * a different module's service.
     */
    public void cancelFutureEnrollments(Long memberId)
    {
        var today = LocalDate.now();
        var now   = Instant.now();

        for (var enrollment : classEnrollmentRepository.findActiveFutureByMember(memberId, EnrollmentStatus.ENROLLED, today))
        {
            enrollment.setStatus(EnrollmentStatus.CANCELLED);
            enrollment.setCancelledAt(now);

            promoteNext(enrollment.getClassSessionId());
        }
    }

    /**
     * Expires NOTIFIED reservations past their deadline and promotes the next member
     * for each one freed. Package-private (not the literal Java "private" the plan's
     * prose uses) so WaitlistService, in the same package, calls it too: it is invoked
     * "solo en rutas de escritura (inscribir, unirse, confirmar, cancelar)", never a
     * third @Scheduled task (02-Modulos §2.9 fixes the count at two).
     */
    void refreshWaitlist(Long sessionId)
    {
        var now     = Instant.now();
        var expired = waitlistEntryRepository.findByClassSessionIdAndStatusAndConfirmationDeadlineBefore(
                sessionId, WaitlistStatus.NOTIFIED, now);

        for (var entry : expired)
        {
            entry.setStatus(WaitlistStatus.EXPIRED);
            entry.setResolvedAt(now);

            promoteNext(sessionId);
        }
    }

    /**
     * "No permitir una cuarta inscripción en la misma semana si el límite es de 3"
     * (Enunciado), counted over the ISO week (Monday-Sunday) of the requested session,
     * not of today.
     */
    private void assertWeeklyLimit(Long memberId, LocalDate sessionDate)
    {
        var weeklyLimit = membershipService.weeklyClassLimit(memberId);

        if (weeklyLimit == MembershipPlan.UNLIMITED_CLASSES)
        {
            return;
        }

        var monday = sessionDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var sunday = monday.plusDays(6);
        var count  = classEnrollmentRepository.countWeeklyActive(memberId, monday, sunday, EnrollmentStatus.CANCELLED);

        if (count >= weeklyLimit)
        {
            throw new BusinessException(ErrorCode.WEEKLY_LIMIT_REACHED);
        }
    }

    /**
     * Notifies the next WAITING member of the freed seat: ordered by plan tier (Elite
     * before Premium) and then request time, "dándole prioridad a los socios con Plan
     * Élite" (Enunciado). Package-private: WaitlistService's confirmation flow shares
     * this exact promotion after refreshing.
     */
    void promoteNext(Long sessionId)
    {
        var waiting = waitlistEntryRepository.findByClassSessionIdAndStatusOrderByRequestedAtAsc(sessionId,
                                                                                                  WaitlistStatus.WAITING);

        if (waiting.isEmpty())
        {
            return;
        }

        var tiers = membershipService.findActiveTiers(waiting.stream().map(WaitlistEntry::getMemberId).toList());
        var next  = waiting.stream().min(WaitlistEntry.byPlanPriority(tiers)).orElseThrow();

        var now = Instant.now();

        next.setStatus(WaitlistStatus.NOTIFIED);
        next.setNotifiedAt(now);
        next.setConfirmationDeadline(now.plus(gymProperties.classes().waitlistConfirmationMinutes(), ChronoUnit.MINUTES));

        // ponytail: WAITLIST_SEAT_AVAILABLE debiera enviarse por correo; el módulo
        // notification no existe todavía (02-Modulos §2.9). El estado NOTIFIED con su
        // notified_at es lo que queda materializado mientras tanto.
    }

    ClassEnrollment findOrFail(Long enrollmentId)
    {
        return classEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND));
    }
}
