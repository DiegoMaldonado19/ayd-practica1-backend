package com.fitness.app.training.dto;

import com.fitness.app.training.model.AssignmentEndReason;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** "DELETE /trainer-assignments/{id}: cierra la asignación vigente con su motivo" (§3.7). */
public record CloseAssignmentRequest(
    @NotNull
    @JsonProperty("end_reason")
    AssignmentEndReason endReason
)
{
}