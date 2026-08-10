package com.fitness.app.classes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** "Cancela la sesión con motivo y notifica a los inscritos" (§3.6): ck_session_cancel demands the reason. */
public record SessionCancellationRequest(
    @NotBlank @Size(max = 200)
    @JsonProperty("cancellation_reason")
    String cancellationReason
)
{
}
