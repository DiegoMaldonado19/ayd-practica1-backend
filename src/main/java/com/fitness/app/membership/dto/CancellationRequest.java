package com.fitness.app.membership.dto;

import com.fitness.app.membership.model.CancellationReason;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /memberships/{id}/cancellations. The reason is optional because the
 * statement says so - "y, opcionalmente, un motivo de cancelación, útil para los
 * reportes administrativos" - while the date is not: the service always writes it.
 */
public record CancellationRequest(                 CancellationReason cancellationReason,
                                  @Size(max = 300) String             notes)
{
}
