package com.fitness.app.training.dto;

import com.fitness.app.training.model.MuscleGroup;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Creates or corrects an exercise. active is not here: it is the baja lógica DELETE. */
public record ExerciseRequest(
    @NotBlank @Size(max = 30)
    String            code,

    @NotBlank @Size(max = 80)
    String            name,

    @NotNull
    @JsonProperty("muscle_group")
    MuscleGroup       muscleGroup,

    @Size(max = 300)
    String            description,

    @Size(max = 255)
    @JsonProperty("video_url")
    String            videoUrl
)
{
}