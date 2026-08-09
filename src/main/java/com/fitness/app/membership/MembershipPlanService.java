package com.fitness.app.membership;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.membership.dto.CreateMembershipPlanRequest;
import com.fitness.app.membership.dto.MembershipPlanResponse;
import com.fitness.app.membership.dto.UpdateMembershipPlanRequest;
import com.fitness.app.membership.model.MembershipPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The plan catalog of §3.3: what the administrator configures and what every role
 * reads before contracting.
 *
 * A plan is never deleted, only deactivated: contracts already signed point at it and
 * fk_membership_plan is ON DELETE RESTRICT.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MembershipPlanService
{
    private final MembershipPlanRepository membershipPlanRepository;

    @Transactional(readOnly = true)
    public Page<MembershipPlanResponse> search(Boolean active, Pageable pageable)
    {
        return membershipPlanRepository.search(active, pageable)
                .map(MembershipPlanResponse::from);
    }

    @Transactional(readOnly = true)
    public MembershipPlanResponse findById(Long membershipPlanId)
    {
        return MembershipPlanResponse.from(findOrFail(membershipPlanId));
    }

    public MembershipPlanResponse create(CreateMembershipPlanRequest request)
    {
        if (membershipPlanRepository.existsByCode(request.code()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ya existe un plan con ese código.");
        }

        assertBenefitsAreConsistent(request.includesGroupClasses(), request.weeklyClassLimit());

        var plan = new MembershipPlan();

        plan.setCode(request.code());
        plan.setTier(request.tier());
        // The DDL defaults active and created_at, but the entity maps them, so
        // Hibernate sends them in the INSERT and a null would break NOT NULL.
        plan.setActive(true);
        plan.setCreatedAt(Instant.now());
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setBillingPeriod(request.billingPeriod());
        plan.setPrice(request.price());
        plan.setIncludesGroupClasses(request.includesGroupClasses());
        plan.setWeeklyClassLimit(request.weeklyClassLimit());
        plan.setIncludesPersonalTrainer(request.includesPersonalTrainer());

        return MembershipPlanResponse.from(membershipPlanRepository.save(plan));
    }

    /**
     * Name, price, periodicity and benefits (§3.3). The new price only reaches
     * contracts signed from now on: membership.paid_price froze the old ones.
     */
    public MembershipPlanResponse update(Long membershipPlanId, UpdateMembershipPlanRequest request)
    {
        var plan = findOrFail(membershipPlanId);

        assertBenefitsAreConsistent(request.includesGroupClasses(), request.weeklyClassLimit());

        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setBillingPeriod(request.billingPeriod());
        plan.setPrice(request.price());
        plan.setIncludesGroupClasses(request.includesGroupClasses());
        plan.setWeeklyClassLimit(request.weeklyClassLimit());
        plan.setIncludesPersonalTrainer(request.includesPersonalTrainer());

        return MembershipPlanResponse.from(plan);
    }

    public MembershipPlanResponse changeStatus(Long membershipPlanId, boolean active)
    {
        var plan = findOrFail(membershipPlanId);

        // Deactivating only closes the plan to new contracts: the ones in force keep
        // running until they expire, which is why nothing else is touched here.
        plan.setActive(active);

        return MembershipPlanResponse.from(plan);
    }

    /** The entity, for the sibling service that needs the price and the billing period. */
    MembershipPlan findOrFail(Long membershipPlanId)
    {
        return membershipPlanRepository.findById(membershipPlanId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND));
    }

    /**
     * ck_plan_limit_ok: a plan without group classes cannot carry a weekly limit. It
     * is checked here and not with @AssertTrue on each record so the rule has a single
     * definition for both the alta and the edit.
     */
    private static void assertBenefitsAreConsistent(boolean includesGroupClasses, Short weeklyClassLimit)
    {
        if (!includesGroupClasses && weeklyClassLimit != null)
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                                        "Un plan sin clases grupales no puede tener límite semanal de clases.");
        }
    }
}
