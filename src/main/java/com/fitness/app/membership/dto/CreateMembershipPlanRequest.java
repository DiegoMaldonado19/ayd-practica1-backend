package com.fitness.app.membership.dto;

import com.fitness.app.membership.model.BillingPeriod;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of POST /membership-plans. It carries code and tier, which PUT does not:
 * both identify the plan and are unique, so they are fixed at creation.
 *
 * weeklyClassLimit null means unlimited when the plan includes classes, and the
 * service rejects it on a plan that does not (ck_plan_limit_ok).
 */
public record CreateMembershipPlanRequest(@NotBlank @Size(max = 20)                 String        code,
                                          @NotBlank @Size(max = 60)                 String        name,
                                          @Size(max = 300)                          String        description,
                                          @NotNull                                  BillingPeriod billingPeriod,
                                          @NotNull @PositiveOrZero
                                          @Digits(integer = 8, fraction = 2)        BigDecimal    price,
                                          @NotNull @Positive                        Short         tier,
                                          @NotNull                                  Boolean       includesGroupClasses,
                                          @Positive                                 Short         weeklyClassLimit,
                                          @NotNull                                  Boolean       includesPersonalTrainer)
{
}
