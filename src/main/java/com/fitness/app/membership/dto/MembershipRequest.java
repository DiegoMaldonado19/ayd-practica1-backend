package com.fitness.app.membership.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of POST /memberships. It carries no price and no expiry date: paid_price is
 * the plan's current price and end_date comes from the billing period, so neither
 * can be dictated from outside.
 *
 * startDate null means today, which is the counter case.
 */
public record MembershipRequest(@NotNull         Long      memberId,
                                @NotNull         Long      membershipPlanId,
                                                 LocalDate startDate,
                                @Size(max = 300) String    notes)
{
}
