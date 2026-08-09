package com.fitness.app.membership.dto;

import com.fitness.app.membership.model.CancellationReason;
import com.fitness.app.membership.model.Membership;
import com.fitness.app.membership.model.MembershipStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The contract as the interface sees it: "detalle del contrato, con beneficios y días
 * restantes" (§3.3). The benefits travel inside plan, so there is no second call to
 * the catalog.
 *
 * This is also the record the other modules receive from
 * MembershipService.findActiveMembership: one shape instead of a twin view record
 * with the same fields.
 *
 * daysRemaining is derived and never stored (04-Base-de-Datos §6).
 */
public record MembershipResponse(Long                   membershipId,
                                 Long                   memberId,
                                 MembershipPlanResponse plan,
                                 BigDecimal             paidPrice,
                                 LocalDate              startDate,
                                 LocalDate              endDate,
                                 MembershipStatus       status,
                                 long                   daysRemaining,
                                 LocalDate              cancelledOn,
                                 CancellationReason     cancellationReason,
                                 String                 notes)
{
    public static MembershipResponse from(Membership membership)
    {
        return new MembershipResponse(membership.getMembershipId(),
                                      membership.getMemberId(),
                                      MembershipPlanResponse.from(membership.getPlan()),
                                      membership.getPaidPrice(),
                                      membership.getStartDate(),
                                      membership.getEndDate(),
                                      membership.getStatus(),
                                      membership.daysRemaining(LocalDate.now()),
                                      membership.getCancelledOn(),
                                      membership.getCancellationReason(),
                                      membership.getNotes());
    }
}
