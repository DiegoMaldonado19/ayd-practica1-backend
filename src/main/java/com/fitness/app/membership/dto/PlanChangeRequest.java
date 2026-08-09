package com.fitness.app.membership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /memberships/{id}/plan-changes. Whether it is an upgrade or a
 * downgrade is read from the tier of both plans, so the caller does not declare it.
 */
public record PlanChangeRequest(@NotNull         Long   membershipPlanId,
                                @Size(max = 300) String notes)
{
}
