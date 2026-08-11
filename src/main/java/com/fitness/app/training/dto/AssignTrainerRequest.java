package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** "POST /trainer-assignments: asigna entrenador a un socio" (§3.7). Both ends travel. */
public record AssignTrainerRequest(
    @NotNull
    @JsonProperty("member_id")
    Long memberId,

    @NotNull
    @JsonProperty("trainer_id")
    Long trainerId
)
{
}