package com.fitness.app.classes;

import com.fitness.app.classes.dto.ClassEnrollmentResponse;
import com.fitness.app.classes.dto.WaitlistEntryResponse;
import com.fitness.app.classes.dto.WaitlistRequest;
import com.fitness.app.classes.model.WaitlistEntry;
import com.fitness.app.classes.model.WaitlistStatus;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.model.PlanBenefit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Unirse, abandonar, confirmar y listar la cola ordenada por prioridad de plan
 * (Enunciado, "lista de espera").
 */
@Service
@RequiredArgsConstructor
@Transactional
public class WaitlistService
{
    /** WAITING/NOTIFIED are the only states a member can still be resolved from. */
    private static final List<WaitlistStatus> PENDING = List.of(WaitlistStatus.WAITING, WaitlistStatus.NOTIFIED);

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final ClassSessionService     classSessionService;
    private final MemberService           memberService;
    private final MembershipService       membershipService;
    private final EnrollmentService       enrollmentService;

    /** "Se une a la lista de espera [...] solo si la clase está llena" (Enunciado). */
    public WaitlistEntryResponse join(Long sessionId, WaitlistRequest request, AuthenticatedUser principal)
    {
        enrollmentService.refreshWaitlist(sessionId);

        var session = classSessionService.findOrFail(sessionId);

        memberService.findById(request.memberId(), principal);
        membershipService.findActiveMembership(request.memberId());

        if (!membershipService.hasBenefit(request.memberId(), PlanBenefit.GROUP_CLASSES))
        {
            throw new BusinessException(ErrorCode.PLAN_BENEFIT_NOT_INCLUDED);
        }

        if (enrollmentService.isActivelyEnrolled(sessionId, request.memberId()))
        {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED);
        }

        var existingEntry = waitlistEntryRepository.findByClassSessionIdAndMemberId(sessionId, request.memberId());

        if (existingEntry.isPresent() && PENDING.contains(existingEntry.get().getStatus()))
        {
            throw new BusinessException(ErrorCode.ALREADY_IN_WAITLIST);
        }

        if (classSessionService.seatsTaken(sessionId) < session.getMaxCapacity())
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                                        "La sesión tiene cupo disponible: inscríbete directamente.");
        }

        var entry = existingEntry.orElseGet(WaitlistEntry::new);

        entry.setClassSessionId(sessionId);
        entry.setMemberId(request.memberId());
        entry.setStatus(WaitlistStatus.WAITING);
        entry.setRequestedAt(Instant.now());
        entry.setNotifiedAt(null);
        entry.setConfirmationDeadline(null);
        entry.setResolvedAt(null);

        return WaitlistEntryResponse.from(waitlistEntryRepository.save(entry));
    }

    /** "Abandona la lista de espera" (§3.6). A NOTIFIED member gives up their reserved seat, freeing it for the next. */
    public void leave(Long waitlistEntryId, AuthenticatedUser principal)
    {
        var entry = findOrFail(waitlistEntryId);

        memberService.findById(entry.getMemberId(), principal);

        if (!PENDING.contains(entry.getStatus()))
        {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        var wasReservingASeat = entry.getStatus() == WaitlistStatus.NOTIFIED;

        entry.setStatus(WaitlistStatus.CANCELLED);
        entry.setResolvedAt(Instant.now());

        if (wasReservingASeat)
        {
            enrollmentService.promoteNext(entry.getClassSessionId());
        }
    }

    /** "Confirma el cupo liberado dentro de la ventana de confirmación" (§3.6). */
    public ClassEnrollmentResponse confirm(Long waitlistEntryId, AuthenticatedUser principal)
    {
        var entry = findOrFail(waitlistEntryId);

        memberService.findById(entry.getMemberId(), principal);

        // Expires this same entry first if its deadline already lapsed, which is what
        // lets the check below answer with the documented error instead of a stale one.
        enrollmentService.refreshWaitlist(entry.getClassSessionId());

        if (entry.getStatus() != WaitlistStatus.NOTIFIED || Instant.now().isAfter(entry.getConfirmationDeadline()))
        {
            throw new BusinessException(ErrorCode.WAITLIST_CONFIRMATION_EXPIRED);
        }

        entry.setStatus(WaitlistStatus.PROMOTED);
        entry.setResolvedAt(Instant.now());

        return enrollmentService.confirmFromWaitlist(entry.getClassSessionId(), entry.getMemberId(), principal);
    }

    /**
     * "Cola de espera, ya ordenada por prioridad de plan y hora de solicitud" (§3.6).
     * The order is resolved in Java because the tier lives in membership's
     * MembershipPlan, and 02-Modulos §1 forbids joining across module tables; the
     * queue of one session is small enough that paging the already-sorted list in
     * memory is simpler than a second round trip.
     */
    @Transactional(readOnly = true)
    public Page<WaitlistEntryResponse> findBySession(Long sessionId, Pageable pageable)
    {
        classSessionService.findOrFail(sessionId);

        var pending = waitlistEntryRepository.findByClassSessionIdAndStatusInOrderByRequestedAtAsc(sessionId, PENDING);
        var tiers   = membershipService.findActiveTiers(pending.stream().map(WaitlistEntry::getMemberId).toList());

        var sorted = pending.stream()
                .sorted(WaitlistEntry.byPlanPriority(tiers))
                .map(WaitlistEntryResponse::from)
                .toList();

        var start = (int) Math.min(pageable.getOffset(), sorted.size());
        var end   = (int) Math.min(start + pageable.getPageSize(), sorted.size());

        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }

    private WaitlistEntry findOrFail(Long waitlistEntryId)
    {
        return waitlistEntryRepository.findById(waitlistEntryId)
                .orElseThrow(() -> new BusinessException(ErrorCode.WAITLIST_ENTRY_NOT_FOUND));
    }
}
