package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * "Transferir la cartera de un entrenador a otro" (§3.2). The trainer being emptied is
 * the one in the path; only the destination travels in the body.
 */
public record MemberTransferRequest(
    @NotNull
    @JsonProperty("to_trainer_id")
    Long toTrainerId
)
{
}
