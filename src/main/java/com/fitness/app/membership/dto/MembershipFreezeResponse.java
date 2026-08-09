package com.fitness.app.membership.dto;

import com.fitness.app.membership.model.FreezeReason;
import com.fitness.app.membership.model.MembershipFreeze;

import java.time.LocalDate;

/**
 * One freeze period. frozenDays and inProgress are derived from the two dates, which
 * is why membership_freeze has neither column.
 */
public record MembershipFreezeResponse(Long         membershipFreezeId,
                                       Long         membershipId,
                                       FreezeReason reason,
                                       String       reasonDetail,
                                       LocalDate    requestedOn,
                                       LocalDate    startDate,
                                       LocalDate    expectedEndDate,
                                       LocalDate    reactivatedOn,
                                       boolean      inProgress,
                                       long         frozenDays)
{
    public static MembershipFreezeResponse from(MembershipFreeze freeze, LocalDate today)
    {
        return new MembershipFreezeResponse(freeze.getMembershipFreezeId(),
                                            freeze.getMembershipId(),
                                            freeze.getReason(),
                                            freeze.getReasonDetail(),
                                            freeze.getRequestedOn(),
                                            freeze.getStartDate(),
                                            freeze.getExpectedEndDate(),
                                            freeze.getReactivatedOn(),
                                            freeze.getReactivatedOn() == null,
                                            freeze.frozenDays(today));
    }
}
