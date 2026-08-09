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
 * Body of PUT /membership-plans/{id}: "modifica nombre, precio, periodicidad y
 * beneficios" (§3.3). code and tier are absent on purpose - contracts already signed
 * reference the plan by id, and tier is the waitlist priority of the whole catalog.
 *
 * active is absent too: PATCH .../status owns it.
 */
public record UpdateMembershipPlanRequest(@NotBlank @Size(max = 60)          String        name,
                                          @Size(max = 300)                   String        description,
                                          @NotNull                           BillingPeriod billingPeriod,
                                          @NotNull @PositiveOrZero
                                          @Digits(integer = 8, fraction = 2) BigDecimal    price,
                                          @NotNull                           Boolean       includesGroupClasses,
                                          @Positive                          Short         weeklyClassLimit,
                                          @NotNull                           Boolean       includesPersonalTrainer)
{
}
