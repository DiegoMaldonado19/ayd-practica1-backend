package com.fitness.app.membership.dto;

import jakarta.validation.constraints.NotNull;

/** Activates or deactivates the plan without deleting it. The row is never deleted. */
public record MembershipPlanStatusRequest(@NotNull Boolean active)
{
}
