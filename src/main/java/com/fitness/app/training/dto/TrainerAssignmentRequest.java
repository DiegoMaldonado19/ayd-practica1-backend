package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record TrainerAssignmentRequest(
    @NotNull
    @JsonProperty("trainer_id")
    Long trainerId
)
{
}
