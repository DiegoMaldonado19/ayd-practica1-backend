package com.fitness.app.membership.dto;

import com.fitness.app.membership.model.BillingPeriod;
import com.fitness.app.membership.model.MembershipPlan;

import java.math.BigDecimal;

/**
 * The plan as the interface sees it. Serves the catalog, the detail, both writes and
 * the benefits block nested inside a contract: six endpoints, one shape.
 */
public record MembershipPlanResponse(Long          membershipPlanId,
                                     String        code,
                                     String        name,
                                     String        description,
                                     BillingPeriod billingPeriod,
                                     BigDecimal    price,
                                     short         tier,
                                     boolean       includesGroupClasses,
                                     Short         weeklyClassLimit,
                                     boolean       includesPersonalTrainer,
                                     boolean       active)
{
    public static MembershipPlanResponse from(MembershipPlan plan)
    {
        return new MembershipPlanResponse(plan.getMembershipPlanId(),
                                          plan.getCode(),
                                          plan.getName(),
                                          plan.getDescription(),
                                          plan.getBillingPeriod(),
                                          plan.getPrice(),
                                          plan.getTier(),
                                          plan.isIncludesGroupClasses(),
                                          plan.getWeeklyClassLimit(),
                                          plan.isIncludesPersonalTrainer(),
                                          plan.isActive());
    }
}
