package com.fitness.app.membership;

import com.fitness.app.classes.EnrollmentService;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.config.GymProperties;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.membership.dto.CancellationRequest;
import com.fitness.app.membership.dto.FreezeHistoryResponse;
import com.fitness.app.membership.dto.FreezeRequest;
import com.fitness.app.membership.dto.MembershipFreezeResponse;
import com.fitness.app.membership.dto.MembershipRequest;
import com.fitness.app.membership.dto.MembershipResponse;
import com.fitness.app.membership.dto.PlanChangeRequest;
import com.fitness.app.membership.model.Membership;
import com.fitness.app.membership.model.MembershipFreeze;
import com.fitness.app.membership.model.MembershipPlan;
import com.fitness.app.membership.model.MembershipStatus;
import com.fitness.app.membership.model.PlanBenefit;
import com.fitness.app.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The contract and its life cycle: contratación, congelamiento con recálculo de
 * vencimiento, renovación, cambio de plan y cancelación.
 *
 * It is also the module every other one asks "¿este socio tiene derecho a esto?"
 * through findActiveMembership, hasBenefit and weeklyClassLimit (02-Modulos §2.3).
 *
 * Freezing lives here and not in a service of its own because it shares findOrFail
 * and the row-level check with the rest of the life cycle.
 *
 * The row-level rule of §3.3 - a member only reaches their own contracts - is
 * delegated to MemberService.findById, which already answers MEMBER_NOT_FOUND and
 * FORBIDDEN_RESOURCE. A SecurityConfig matcher sees the path, not the row behind it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MembershipService
{
    /** A contract is in force while it is one of these two: uq_membership_in_force. */
    private static final EnumSet<MembershipStatus> IN_FORCE = EnumSet.of(MembershipStatus.ACTIVE,
                                                                         MembershipStatus.FROZEN);

    /** A date no contract can reach: the listing sends it when nobody asked to filter by expiry. */
    private static final LocalDate NO_EXPIRY_FILTER = LocalDate.of(9999, 12, 31);

    private final MembershipRepository       membershipRepository;
    private final MembershipFreezeRepository membershipFreezeRepository;
    private final MembershipPlanService      membershipPlanService;
    private final MemberService              memberService;
    private final NotificationService        notificationService;
    private final GymProperties              gymProperties;

    /**
     * ObjectProvider and not a direct EnrollmentService field: classes.EnrollmentService
     * injects MembershipService directly (it asks about benefits on every enrollment),
     * so a direct reference here in both directions is a bean cycle Spring refuses to
     * build. This is the second cycle of 02-Modulos §3, resolved with a direct call in
     * the documented direction - membership calls classes, never the other way in this
     * flow - just deferred until the bean graph is up.
     */
    private final ObjectProvider<EnrollmentService> enrollmentServiceProvider;

    // -- The contract that the other modules consume (02-Modulos §2.3) ------------

    /**
     * The contract in force of a member, or MEMBERSHIP_NOT_ACTIVE. access, classes,
     * training and nutrition all start here.
     *
     * The expiry date is checked as well as the status: the nightly task may not have
     * run yet, and letting an expired contract pass would open the door the statement
     * closes.
     */
    @Transactional(readOnly = true)
    public MembershipResponse findActiveMembership(Long memberId)
    {
        return MembershipResponse.from(findActiveOrFail(memberId));
    }

    /** "El sistema debe validar [...] si el plan actual del socio incluye ese beneficio". */
    @Transactional(readOnly = true)
    public boolean hasBenefit(Long memberId, PlanBenefit benefit)
    {
        return findActiveOrFail(memberId).getPlan().includes(benefit);
    }

    /** MembershipPlan.UNLIMITED_CLASSES (-1) when the plan sets no weekly cap. */
    @Transactional(readOnly = true)
    public int weeklyClassLimit(Long memberId)
    {
        return findActiveOrFail(memberId).getPlan().effectiveWeeklyClassLimit();
    }

    /**
     * The member ids whose current contract matches, so directory can serve the
     * plan_code and membership_status filters of §3.2 without reading this module's
     * tables. Null means "no membership filter was asked for".
     */
    @Transactional(readOnly = true)
    public List<Long> findMemberIds(String planCode, MembershipStatus membershipStatus)
    {
        if (planCode == null && membershipStatus == null)
        {
            return null;
        }

        return membershipRepository.findMemberIdsByCurrentMembership(planCode == null ? "" : planCode,
                                                                     membershipStatus);
    }

    /**
     * The plan tier of each member's contract in force, in one query. classes uses it
     * to order a waitlist by priority (Elite before Premium) without mapping
     * membership's entities - a member with no contract in force is simply absent from
     * the map and never promoted.
     */
    @Transactional(readOnly = true)
    public Map<Long, Short> findActiveTiers(Collection<Long> memberIds)
    {
        if (memberIds.isEmpty())
        {
            return Map.of();
        }

        return membershipRepository.findInForceByMemberIdIn(memberIds, IN_FORCE).stream()
                .collect(Collectors.toMap(Membership::getMemberId, m -> m.getPlan().getTier()));
    }

    // -- Contracts ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<MembershipResponse> search(MembershipStatus status,
                                           Long             planId,
                                           Integer          expiringInDays,
                                           Pageable         pageable)
    {
        // "Próxima a vencer" is a derived state and never a stored one (04-BD §6):
        // it is a due date on a contract still in force, so an absent status defaults
        // to ACTIVE while an explicit one still wins.
        var filtering       = expiringInDays != null;
        var expiringBefore  = filtering ? LocalDate.now().plusDays(expiringInDays) : NO_EXPIRY_FILTER;
        var effectiveStatus = filtering && status == null ? MembershipStatus.ACTIVE : status;

        return membershipRepository.search(effectiveStatus, planId, expiringBefore, pageable)
                .map(MembershipResponse::from);
    }

    @Transactional(readOnly = true)
    public MembershipResponse findById(Long membershipId, AuthenticatedUser principal)
    {
        return MembershipResponse.from(findAccessible(membershipId, principal));
    }

    @Transactional(readOnly = true)
    public Page<MembershipResponse> findHistory(Long memberId, AuthenticatedUser principal, Pageable pageable)
    {
        memberService.findById(memberId, principal);

        return membershipRepository.findHistory(memberId, pageable)
                .map(MembershipResponse::from);
    }

    /**
     * Contrata una membresía para un socio. paid_price is the plan's price today, not
     * a figure the caller sends: that is what freezes the agreed value against later
     * price changes.
     */
    public MembershipResponse create(MembershipRequest request, AuthenticatedUser principal)
    {
        // Validates that the member file exists before writing anything.
        memberService.findById(request.memberId(), principal);

        if (membershipRepository.existsByMemberIdAndStatusIn(request.memberId(), IN_FORCE))
        {
            throw new BusinessException(ErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
        }

        var plan = membershipPlanService.findOrFail(request.membershipPlanId());

        assertPlanIsSellable(plan);

        var membership = sign(request.memberId(),
                              plan,
                              request.startDate() == null ? LocalDate.now() : request.startDate(),
                              request.notes(),
                              principal);

        return MembershipResponse.from(membershipRepository.save(membership));
    }

    /**
     * Renueva: crea el contrato siguiente encadenado al actual. The one in force is
     * left EXPIRED because uq_membership_in_force does not admit two contracts at
     * once, and the new one starts where the old one ended so no day is lost.
     */
    public MembershipResponse renew(Long membershipId, AuthenticatedUser principal)
    {
        var current = findAccessible(membershipId, principal);

        assertRenewable(current);

        var today    = LocalDate.now();
        var newStart = current.getEndDate().isAfter(today) ? current.getEndDate() : today;

        retire(current);

        var renewal = sign(current.getMemberId(), current.getPlan(), newStart, current.getNotes(), principal);

        return MembershipResponse.from(membershipRepository.save(renewal));
    }

    /**
     * Cambio de plan (upgrade o downgrade). The new contract starts today with the
     * target plan's full period: there is no proration, and the superseded contract
     * keeps its own end_date because shortening it to today would break
     * ck_membership_dates on a plan changed the same day it was signed.
     */
    public MembershipResponse changePlan(Long membershipId, PlanChangeRequest request, AuthenticatedUser principal)
    {
        var current = findAccessible(membershipId, principal);

        assertInForceAndActive(current);

        var plan = membershipPlanService.findOrFail(request.membershipPlanId());

        assertPlanIsSellable(plan);

        if (plan.getMembershipPlanId().equals(current.getPlan().getMembershipPlanId()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El socio ya tiene contratado ese plan.");
        }

        retire(current);

        var change = sign(current.getMemberId(), plan, LocalDate.now(), request.notes(), principal);

        return MembershipResponse.from(membershipRepository.save(change));
    }

    /**
     * Cancela definitivamente, con motivo. CANCELLED is terminal: the statement calls
     * it "definitivo", and ck_membership_cancel demands the date travel with it.
     */
    public MembershipResponse cancel(Long membershipId, CancellationRequest request, AuthenticatedUser principal)
    {
        var membership = findAccessible(membershipId, principal);

        if (membership.getStatus() == MembershipStatus.CANCELLED)
        {
            throw new BusinessException(ErrorCode.MEMBERSHIP_CANCELLED);
        }

        membership.setStatus(MembershipStatus.CANCELLED);
        membership.setCancelledOn(LocalDate.now());
        membership.setCancellationReason(request.cancellationReason());

        if (request.notes() != null)
        {
            membership.setNotes(request.notes());
        }

        return MembershipResponse.from(membership);
    }

    // -- Freezes ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public FreezeHistoryResponse findFreezes(Long membershipId, AuthenticatedUser principal)
    {
        findAccessible(membershipId, principal);

        var today   = LocalDate.now();
        var freezes = membershipFreezeRepository.findByMembershipIdOrderByStartDateDesc(membershipId);
        var inCycle = withinCycle(freezes, today);

        return new FreezeHistoryResponse(freezes.stream().map(freeze -> MembershipFreezeResponse.from(freeze, today)).toList(),
                                         inCycle.stream().mapToLong(freeze -> freeze.frozenDays(today)).sum(),
                                         inCycle.size(),
                                         gymProperties.freeze().maxDaysPerCycle(),
                                         gymProperties.freeze().maxCountPerCycle(),
                                         gymProperties.freeze().cycleDays());
    }

    /**
     * Congela la membresía. The expiry date is deliberately untouched: "durante este
     * estado no se descuenta tiempo de vigencia del plan". What extends it is the real
     * reactivation date.
     *
     * "Durante este estado [...] el socio no puede [...] inscribirse a clases [...]
     * hasta que reactive su membresía" (Enunciado): freezing also cancels the member's
     * future class enrollments, calling classes in the direction 02-Modulos §3 fixes.
     */
    public MembershipFreezeResponse freeze(Long membershipId, FreezeRequest request, AuthenticatedUser principal)
    {
        var membership = findAccessible(membershipId, principal);

        assertInForceAndActive(membership);

        var today     = LocalDate.now();
        var startDate = request.startDate() == null ? today : request.startDate();

        if (request.expectedEndDate() != null && request.expectedEndDate().isBefore(startDate))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                                        "La fecha estimada de reactivación no puede ser anterior al inicio.");
        }

        assertCycleAllowsAnotherFreeze(membershipId, startDate, request.expectedEndDate(), today);

        var freeze = new MembershipFreeze();

        freeze.setMembershipId(membershipId);
        freeze.setReason(request.reason());
        freeze.setReasonDetail(request.reasonDetail());
        // The DDL defaults requested_on, but the entity maps it, so Hibernate sends it.
        freeze.setRequestedOn(today);
        freeze.setStartDate(startDate);
        freeze.setExpectedEndDate(request.expectedEndDate());
        freeze.setAuthorizedByUserId(principal.appUserId());

        membership.setStatus(MembershipStatus.FROZEN);

        cancelFutureEnrollments(membership.getMemberId());
        notificationService.membershipFrozen(membership.getMemberId(), startDate, request.expectedEndDate());

        return MembershipFreezeResponse.from(membershipFreezeRepository.save(freeze), today);
    }

    /**
     * Reactiva y recalcula el vencimiento sumando los días congelados: "al reactivarse,
     * la nueva fecha de vencimiento se recalcula sumando el tiempo que permaneció
     * congelada".
     */
    public MembershipResponse reactivate(Long membershipId, AuthenticatedUser principal)
    {
        var membership = findAccessible(membershipId, principal);

        if (membership.getStatus() != MembershipStatus.FROZEN)
        {
            throw new BusinessException(ErrorCode.FREEZE_NOT_IN_PROGRESS);
        }

        var today  = LocalDate.now();
        var freeze = membershipFreezeRepository.findByMembershipIdAndReactivatedOnIsNull(membershipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FREEZE_NOT_IN_PROGRESS));

        var frozenDays = freeze.frozenDays(today);

        freeze.setReactivatedOn(today);
        freeze.setReactivatedByUserId(principal.appUserId());

        membership.setEndDate(membership.getEndDate().plusDays(frozenDays));
        membership.setStatus(MembershipStatus.ACTIVE);

        notificationService.membershipReactivated(membership.getMemberId(), membership.getEndDate());

        return MembershipResponse.from(membership);
    }

    // -- Scheduled ----------------------------------------------------------------

    /**
     * Pasa a EXPIRED las membresías vencidas y avisa: "volver a notificar el día en
     * que la membresía pasa a estado vencida" (Enunciado).
     *
     * 02-Modulos §2.9 lists both scheduled tasks under notification, but its own
     * dependency matrix (§3) lets notification consume directory and nothing else,
     * and the canonical service of 01-Refinamiento §7 is MembershipService injecting
     * NotificationService. They live here so the arrow keeps pointing that way: the
     * contract state is membership's to change, the notice is notification's to send.
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void expireMemberships()
    {
        var today = LocalDate.now();
        // Read before the bulk update: afterwards these rows no longer match it.
        var due   = membershipRepository.findDueToExpire(today, MembershipStatus.ACTIVE).stream()
                .collect(Collectors.toMap(Membership::getMemberId, Membership::getEndDate));

        var expired = membershipRepository.expireDueContracts(today, MembershipStatus.ACTIVE, MembershipStatus.EXPIRED);

        due.forEach((memberId, endDate) ->
        {
            cancelFutureEnrollments(memberId);
            notificationService.membershipExpired(memberId, endDate);
        });

        log.info("Membresías vencidas: {}", expired);
    }

    /**
     * "El sistema debe notificar al socio con un margen de días antes del vencimiento
     * (por ejemplo, 5 días antes)" (Enunciado). The margin is
     * gym.membership.expiry-notice-days.
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void notifyExpiringMemberships()
    {
        var noticeDays = gymProperties.membership().expiryNoticeDays();
        var noticeDate = LocalDate.now().plusDays(noticeDays);

        var expiring = membershipRepository.findExpiringOn(noticeDate, MembershipStatus.ACTIVE);

        expiring.forEach(membership -> notificationService.membershipExpiring(
                membership.getMemberId(), membership.getEndDate(), noticeDays));

        log.info("Avisos de vencimiento próximo: {}", expiring.size());
    }

    // -- Helpers ------------------------------------------------------------------

    /**
     * "El sistema debe [...] cancelar las inscripciones futuras" - the direct call of
     * 02-Modulos §3, ciclo #2: membership calls classes, and classes never calls back
     * in this flow. ObjectProvider defers the lookup past bean construction, which is
     * what breaks the cycle (see the field's Javadoc above).
     */
    private void cancelFutureEnrollments(Long memberId)
    {
        enrollmentServiceProvider.ifAvailable(enrollmentService -> enrollmentService.cancelFutureEnrollments(memberId));
    }

    /**
     * The most recent contract: applies assertInForceAndActive to check if it is ACTIVE
     * and distinguish FROZEN, EXPIRED and CANCELLED with their specific error codes.
     * Also guards against calendar drift: the nightly task may not have run yet.
     */
    private Membership findActiveOrFail(Long memberId)
    {
        var membership = membershipRepository.findFirstByMemberIdOrderByMembershipIdDesc(memberId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBERSHIP_NOT_ACTIVE));

        assertInForceAndActive(membership);

        if (membership.getEndDate().isBefore(LocalDate.now()))
        {
            throw new BusinessException(ErrorCode.MEMBERSHIP_EXPIRED);
        }

        return membership;
    }

    /**
     * Steps the contract in force aside so the next one can take its place.
     *
     * The flush is not decoration: Hibernate runs every insert of the transaction
     * before any update, so without it the new row reaches uq_membership_in_force
     * while this one is still ACTIVE and the renewal dies with a constraint
     * violation instead of succeeding.
     */
    private void retire(Membership membership)
    {
        membership.setStatus(MembershipStatus.EXPIRED);
        membershipRepository.flush();
    }

    /** A new contract: same shape for the alta, the renewal and the plan change. */
    private Membership sign(Long              memberId,
                            MembershipPlan    plan,
                            LocalDate         startDate,
                            String            notes,
                            AuthenticatedUser principal)
    {
        var membership = new Membership();

        membership.setMemberId(memberId);
        membership.setPlan(plan);
        // The price agreed in *this* contract, which may differ from the plan's tariff
        // later on. That is why no plan price history table is needed.
        membership.setPaidPrice(plan.getPrice());
        membership.setStartDate(startDate);
        membership.setEndDate(startDate.plusDays(plan.getBillingPeriod().getDays()));
        membership.setStatus(MembershipStatus.ACTIVE);
        membership.setNotes(notes);
        membership.setCreatedByUserId(principal.appUserId());
        membership.setCreatedAt(Instant.now());

        return membership;
    }

    /**
     * The contract, after checking that this caller may reach it. Loading the member
     * file is what tells a socio apart from the owner of the contract.
     */
    private Membership findAccessible(Long membershipId, AuthenticatedUser principal)
    {
        var membership = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBERSHIP_NOT_FOUND));

        memberService.findById(membership.getMemberId(), principal);

        return membership;
    }

    /**
     * The state machine in one place: each state answers with its own code instead of
     * a generic one, which is what lets the interface offer the right button.
     */
    private static void assertInForceAndActive(Membership membership)
    {
        switch (membership.getStatus())
        {
            case ACTIVE    -> { }
            case FROZEN    -> throw new BusinessException(ErrorCode.MEMBERSHIP_FROZEN);
            case EXPIRED   -> throw new BusinessException(ErrorCode.MEMBERSHIP_EXPIRED);
            case CANCELLED -> throw new BusinessException(ErrorCode.MEMBERSHIP_CANCELLED);
        }
    }

    /**
     * Renewing is the way out of an expired contract, so EXPIRED is allowed here and
     * nowhere else. A frozen one must be reactivated first, and a cancelled one is
     * terminal.
     */
    private static void assertRenewable(Membership membership)
    {
        if (membership.getStatus() == MembershipStatus.FROZEN
                || membership.getStatus() == MembershipStatus.CANCELLED)
        {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }
    }

    private static void assertPlanIsSellable(MembershipPlan plan)
    {
        if (!plan.isActive())
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El plan está desactivado.");
        }
    }

    /**
     * "El sistema debe limitar la cantidad de veces o de días que una membresía puede
     * congelarse por ciclo". Both limits are checked, and the estimated end date is
     * projected when it comes, so the member learns at request time that the freeze
     * would not fit.
     *
     * The cycle is per contract: membership_freeze hangs from membership, and a
     * renewal opens a fresh one.
     */
    private void assertCycleAllowsAnotherFreeze(Long      membershipId,
                                                LocalDate startDate,
                                                LocalDate expectedEndDate,
                                                LocalDate today)
    {
        var inCycle = withinCycle(membershipFreezeRepository.findByMembershipIdOrderByStartDateDesc(membershipId),
                                  today);

        if (inCycle.size() >= gymProperties.freeze().maxCountPerCycle())
        {
            throw new BusinessException(ErrorCode.FREEZE_LIMIT_REACHED);
        }

        var daysUsed  = inCycle.stream().mapToLong(freeze -> freeze.frozenDays(today)).sum();
        var projected = expectedEndDate == null ? 0 : ChronoUnit.DAYS.between(startDate, expectedEndDate);

        if (daysUsed + projected > gymProperties.freeze().maxDaysPerCycle())
        {
            throw new BusinessException(ErrorCode.FREEZE_LIMIT_REACHED);
        }
    }

    /** The freezes that count against the current cycle window. */
    private List<MembershipFreeze> withinCycle(List<MembershipFreeze> freezes, LocalDate today)
    {
        var cycleStart = today.minusDays(gymProperties.freeze().cycleDays());

        return freezes.stream()
                .filter(freeze -> !freeze.getStartDate().isBefore(cycleStart))
                .toList();
    }
}
