package com.fitness.app.membership.dto;

import com.fitness.app.membership.model.FreezeReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of POST /memberships/{id}/freezes: "indicando un motivo y, opcionalmente, una
 * fecha estimada de reactivación".
 *
 * startDate null means today. expectedEndDate is only an estimate: what recomputes
 * the expiry date is the real reactivation date, not this one.
 */
public record FreezeRequest(@NotNull         FreezeReason reason,
                            @Size(max = 300) String       reasonDetail,
                                             LocalDate    startDate,
                                             LocalDate    expectedEndDate)
{
}
