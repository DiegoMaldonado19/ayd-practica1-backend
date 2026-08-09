package com.fitness.app.membership.dto;

import java.util.List;

/**
 * Answer of GET /memberships/{id}/freezes: "congelamientos del contrato **y días
 * acumulados en el ciclo**" (§3.3).
 *
 * It is not a PagedModel like the rest of the collections because the counters are
 * half the answer, and the interface needs the limits in force to tell the member how
 * much is left before FREEZE_LIMIT_REACHED.
 */
public record FreezeHistoryResponse(List<MembershipFreezeResponse> freezes,
                                    long                           daysUsedInCycle,
                                    int                            freezesUsedInCycle,
                                    int                            maxDaysPerCycle,
                                    int                            maxCountPerCycle,
                                    int                            cycleDays)
{
}
