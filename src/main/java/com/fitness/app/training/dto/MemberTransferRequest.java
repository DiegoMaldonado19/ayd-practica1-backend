package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * Transferir la cartera de un entrenador a otro
 * **/
public record MemberTransferRequest(
    @NotNull
    @JsonProperty("to_trainer_id")
    Long toTrainerId
)
{
}
