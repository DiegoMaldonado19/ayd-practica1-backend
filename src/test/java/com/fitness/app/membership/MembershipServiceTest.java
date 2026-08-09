package com.fitness.app.membership;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.config.GymProperties;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.membership.dto.CancellationRequest;
import com.fitness.app.membership.dto.FreezeRequest;
import com.fitness.app.membership.dto.MembershipRequest;
import com.fitness.app.membership.model.BillingPeriod;
import com.fitness.app.membership.model.CancellationReason;
import com.fitness.app.membership.model.Membership;
import com.fitness.app.membership.model.MembershipFreeze;
import com.fitness.app.membership.model.MembershipPlan;
import com.fitness.app.membership.model.MembershipStatus;
import com.fitness.app.membership.model.FreezeReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

/**
 * The rules the Postman collection cannot show: the freeze quota per cycle, the
 * arithmetic that recomputes the expiry date on reactivation, and which transitions
 * the state machine refuses. Everything else in MembershipService is a pass-through
 * the collection already exercises.
 */
@ExtendWith(MockitoExtension.class)
class MembershipServiceTest
{
    private static final Long MEMBER_ID     = 3L;
    private static final Long MEMBERSHIP_ID = 8L;
    private static final Long PLAN_ID       = 2L;

    @Mock private MembershipRepository       membershipRepository;
    @Mock private MembershipFreezeRepository membershipFreezeRepository;
    @Mock private MembershipPlanService      membershipPlanService;
    @Mock private MemberService              memberService;

    // The real limits of application.yml: 15 days and 2 freezes per 90-day cycle.
    @Spy private GymProperties gymProperties = new GymProperties(new GymProperties.Freeze(15, 2, 90));

    @InjectMocks private MembershipService membershipService;

    @Test
    void refusesASecondContractWhileOneIsInForce()
    {
        // uq_membership_in_force says the same thing at the database level, but the
        // member deserves MEMBERSHIP_ALREADY_ACTIVE and not a constraint violation.
        when(membershipRepository.existsByMemberIdAndStatusIn(anyLong(), anyCollection())).thenReturn(true);

        assertEquals(ErrorCode.MEMBERSHIP_ALREADY_ACTIVE,
                     assertThrows(BusinessException.class,
                                  () -> membershipService.create(contractRequest(), principal())).getErrorCode());
    }

    @Test
    void endsTheContractAfterThePeriodOfThePlan()
    {
        when(membershipRepository.existsByMemberIdAndStatusIn(anyLong(), anyCollection())).thenReturn(false);
        when(membershipPlanService.findOrFail(PLAN_ID)).thenReturn(plan());
        when(membershipRepository.save(any(Membership.class))).thenAnswer(call -> call.getArgument(0));

        var contract = membershipService.create(contractRequest(), principal());

        assertEquals(LocalDate.now().plusDays(BillingPeriod.MONTHLY.getDays()), contract.endDate());
        // paid_price freezes the tariff of today, it is never sent by the caller.
        assertEquals(plan().getPrice(), contract.paidPrice());
    }

    @Test
    void refusesAThirdFreezeInTheSameCycle()
    {
        var membership = membership(MembershipStatus.ACTIVE);

        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));
        when(membershipFreezeRepository.findByMembershipIdOrderByStartDateDesc(MEMBERSHIP_ID))
                .thenReturn(List.of(closedFreeze(20, 3), closedFreeze(60, 4)));

        assertEquals(ErrorCode.FREEZE_LIMIT_REACHED,
                     assertThrows(BusinessException.class,
                                  () -> membershipService.freeze(MEMBERSHIP_ID, freezeRequest(null), principal()))
                             .getErrorCode());
    }

    @Test
    void refusesAFreezeThatWouldExceedTheDaysOfTheCycle()
    {
        var membership = membership(MembershipStatus.ACTIVE);

        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));
        when(membershipFreezeRepository.findByMembershipIdOrderByStartDateDesc(MEMBERSHIP_ID))
                .thenReturn(List.of(closedFreeze(40, 14)));

        // 14 days already used plus the 5 estimated go over the 15 of the cycle, and
        // the member learns it when asking and not when reactivating.
        assertEquals(ErrorCode.FREEZE_LIMIT_REACHED,
                     assertThrows(BusinessException.class,
                                  () -> membershipService.freeze(MEMBERSHIP_ID,
                                                                 freezeRequest(LocalDate.now().plusDays(5)),
                                                                 principal()))
                             .getErrorCode());
    }

    @Test
    void freezingDoesNotDiscountTimeFromThePlan()
    {
        var membership   = membership(MembershipStatus.ACTIVE);
        var originalEnd  = membership.getEndDate();

        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));
        when(membershipFreezeRepository.findByMembershipIdOrderByStartDateDesc(MEMBERSHIP_ID)).thenReturn(List.of());
        when(membershipFreezeRepository.save(any(MembershipFreeze.class))).thenAnswer(call -> call.getArgument(0));

        var freeze = membershipService.freeze(MEMBERSHIP_ID, freezeRequest(null), principal());

        // "Durante este estado no se descuenta tiempo de vigencia del plan": what
        // moves the expiry date is the reactivation, never the freeze.
        assertEquals(MembershipStatus.FROZEN, membership.getStatus());
        assertEquals(originalEnd, membership.getEndDate());
        assertEquals(LocalDate.now(), freeze.startDate());
        assertTrue(freeze.inProgress());
    }

    @Test
    void reactivationAddsTheFrozenDaysToTheExpiry()
    {
        var membership  = membership(MembershipStatus.FROZEN);
        var originalEnd = membership.getEndDate();
        var freeze      = openFreeze(10);

        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));
        when(membershipFreezeRepository.findByMembershipIdAndReactivatedOnIsNull(MEMBERSHIP_ID))
                .thenReturn(Optional.of(freeze));

        var reactivated = membershipService.reactivate(MEMBERSHIP_ID, principal());

        assertEquals(originalEnd.plusDays(10), reactivated.endDate());
        assertEquals(MembershipStatus.ACTIVE, reactivated.status());
        assertEquals(LocalDate.now(), freeze.getReactivatedOn());
    }

    @Test
    void refusesToReactivateAContractThatIsNotFrozen()
    {
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership(MembershipStatus.ACTIVE)));

        assertEquals(ErrorCode.FREEZE_NOT_IN_PROGRESS,
                     assertThrows(BusinessException.class,
                                  () -> membershipService.reactivate(MEMBERSHIP_ID, principal())).getErrorCode());
    }

    @Test
    void renewalChainsTheNextContractAndRetiresTheCurrentOne()
    {
        var current = membership(MembershipStatus.ACTIVE);

        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(current));
        when(membershipRepository.save(any(Membership.class))).thenAnswer(call -> call.getArgument(0));

        var renewal = membershipService.renew(MEMBERSHIP_ID, principal());

        // No day is lost: the new contract starts where the old one ended, and the old
        // one steps aside because uq_membership_in_force allows only one in force.
        assertEquals(current.getEndDate(), renewal.startDate());
        assertEquals(MembershipStatus.EXPIRED, current.getStatus());
        assertEquals(MembershipStatus.ACTIVE, renewal.status());

        // And it steps aside *before* the insert: Hibernate runs every insert of the
        // transaction ahead of any update, so without the flush in between the new row
        // reaches uq_membership_in_force while the old one is still ACTIVE.
        var order = inOrder(membershipRepository);

        order.verify(membershipRepository).flush();
        order.verify(membershipRepository).save(any(Membership.class));
    }

    @Test
    void refusesToRenewAFrozenContract()
    {
        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership(MembershipStatus.FROZEN)));

        assertEquals(ErrorCode.INVALID_STATE_TRANSITION,
                     assertThrows(BusinessException.class,
                                  () -> membershipService.renew(MEMBERSHIP_ID, principal())).getErrorCode());
    }

    @Test
    void cancellationDatesTheContract()
    {
        var membership = membership(MembershipStatus.ACTIVE);

        when(membershipRepository.findById(MEMBERSHIP_ID)).thenReturn(Optional.of(membership));

        var cancelled = membershipService.cancel(MEMBERSHIP_ID,
                                                 new CancellationRequest(CancellationReason.COST, null),
                                                 principal());

        // ck_membership_cancel: CANCELLED and the date must agree in both directions.
        assertEquals(MembershipStatus.CANCELLED, cancelled.status());
        assertEquals(LocalDate.now(), cancelled.cancelledOn());
        assertNotNull(cancelled.cancellationReason());
    }

    @Test
    void cancellationIsTerminal()
    {
        when(membershipRepository.findById(MEMBERSHIP_ID))
                .thenReturn(Optional.of(membership(MembershipStatus.CANCELLED)));

        assertEquals(ErrorCode.MEMBERSHIP_CANCELLED,
                     assertThrows(BusinessException.class,
                                  () -> membershipService.cancel(MEMBERSHIP_ID,
                                                                 new CancellationRequest(null, null),
                                                                 principal()))
                             .getErrorCode());
    }

    private static MembershipRequest contractRequest()
    {
        return new MembershipRequest(MEMBER_ID, PLAN_ID, null, null);
    }

    private static FreezeRequest freezeRequest(LocalDate expectedEndDate)
    {
        return new FreezeRequest(FreezeReason.TRAVEL, "Viaje de trabajo", null, expectedEndDate);
    }

    private static MembershipPlan plan()
    {
        var plan = new MembershipPlan();

        plan.setMembershipPlanId(PLAN_ID);
        plan.setCode("PREMIUM");
        plan.setName("Plan Premium");
        plan.setBillingPeriod(BillingPeriod.MONTHLY);
        plan.setPrice(new BigDecimal("400.00"));
        plan.setTier((short) 2);
        plan.setIncludesGroupClasses(true);
        plan.setWeeklyClassLimit((short) 3);
        plan.setActive(true);

        return plan;
    }

    private static Membership membership(MembershipStatus status)
    {
        var membership = new Membership();

        membership.setMembershipId(MEMBERSHIP_ID);
        membership.setMemberId(MEMBER_ID);
        membership.setPlan(plan());
        membership.setPaidPrice(new BigDecimal("400.00"));
        membership.setStartDate(LocalDate.now().minusDays(10));
        membership.setEndDate(LocalDate.now().plusDays(20));
        membership.setStatus(status);

        return membership;
    }

    /** A freeze that already ended: it started daysAgo and lasted length days. */
    private static MembershipFreeze closedFreeze(int daysAgo, int length)
    {
        var freeze = openFreeze(daysAgo);

        freeze.setReactivatedOn(LocalDate.now().minusDays(daysAgo - (long) length));

        return freeze;
    }

    /** A freeze still in progress, started daysAgo. */
    private static MembershipFreeze openFreeze(int daysAgo)
    {
        var freeze = new MembershipFreeze();

        freeze.setMembershipId(MEMBERSHIP_ID);
        freeze.setReason(FreezeReason.TRAVEL);
        freeze.setStartDate(LocalDate.now().minusDays(daysAgo));
        freeze.setRequestedOn(LocalDate.now().minusDays(daysAgo));

        return freeze;
    }

    private static AuthenticatedUser principal()
    {
        return new AuthenticatedUser(1L, "recepcion", UserRole.RECEPTIONIST);
    }
}
