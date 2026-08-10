package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Assigns a trainer to a member. The member is the one in the path, so only the
 * trainer travels in the body.
 */
public record TrainerAssignmentRequest(
    @NotNull
    @JsonProperty("trainer_id")
    Long trainerId
)
{
}
