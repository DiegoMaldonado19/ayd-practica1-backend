package com.fitness.app.training.dto;

import com.fitness.app.training.model.TrainerAlertType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "POST /trainer-alerts: escala al administrador" (§3.7). The trainer is the caller. */
public record TrainerAlertRequest(
    @NotNull
    @JsonProperty("member_id")
    Long              memberId,

    @NotNull
    @JsonProperty("alert_type")
    TrainerAlertType  alertType,

    @NotBlank @Size(max = 500)
    String            description
)
{
}