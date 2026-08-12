package com.fitness.app.training.dto;

import com.fitness.app.training.model.TrainerNoteType;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** El entrenador deja una observación o recomendación nutricional */
public record TrainerNoteRequest(
    @NotNull
    @JsonProperty("note_type")
    TrainerNoteType noteType,

    @NotBlank @Size(max = 1000)
    String          content,

    @JsonProperty("reference_date")
    LocalDate       referenceDate
)
{
}