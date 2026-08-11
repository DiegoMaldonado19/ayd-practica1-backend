package com.fitness.app.training.dto;

import com.fitness.app.training.model.TrainerAlertStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TrainerAlertStatusRequest(
    @NotNull
    TrainerAlertStatus status,

    @Size(max = 500)
    @JsonProperty("resolution_notes")
    String             resolutionNotes
)
{
}