package com.fitness.app.access.dto;

import com.fitness.app.access.model.AccessChannel;
import jakarta.validation.constraints.NotNull;

/**
 * Check-in request. channel is optional, defaulting to FRONT_DESK.
 * membership_id is resolved by the service through validation.
 */
public record CheckInRequest(
    @NotNull
    Long           memberId,

    AccessChannel  channel
)
{
}
