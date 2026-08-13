package com.fitness.app.membership.dto;

import com.fitness.app.membership.model.FreezeReason;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Body of POST /memberships/{id}/freezes: "indicando un motivo y, opcionalmente, una
 * fecha estimada de reactivación".
 *
 * startDate null means today. expectedEndDate is only an estimate: what recomputes
 * the expiry date is the real reactivation date, not this one.
 *
 * @FutureOrPresent because a freeze cannot be backdated: the reactivation adds the
 * elapsed days to the expiry date, so a start date a year old handed out a year of
 * membership. Null is still accepted and still means today.
 */
public record FreezeRequest(@NotNull                  FreezeReason reason,
                            @Size(max = 300)          String       reasonDetail,
                            @FutureOrPresent          LocalDate    startDate,
                                                      LocalDate    expectedEndDate)
{
}
