package com.fitness.app.classes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * "Califica la clase y al entrenador. Requiere haber asistido" (§3.6). memberId is not
 * a field: only S may call this route, and the enrolled member is the caller
 * (principal), matching how the socio's own file is resolved everywhere else.
 */
public record ClassRatingRequest(
    @NotNull @Min(1) @Max(5)
    @JsonProperty("class_score")
    Short  classScore,

    @Min(1) @Max(5)
    @JsonProperty("trainer_score")
    Short  trainerScore,

    @Size(max = 500)
    String comment
)
{
}
