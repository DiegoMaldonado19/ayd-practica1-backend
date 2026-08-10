package com.fitness.app.classes.dto;

import com.fitness.app.classes.model.DifficultyLevel;
import com.fitness.app.classes.model.Discipline;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * "El administrador crea las clases grupales definiendo: nombre [...], entrenador a
 * cargo, horario, duración y cupo máximo" (Enunciado). difficultyLevel is optional and
 * defaults to BEGINNER, matching the column default.
 */
public record GroupClassRequest(
    @NotBlank @Size(max = 20)
    String            code,

    @NotBlank @Size(max = 60)
    String            name,

    @NotNull
    Discipline        discipline,

    @JsonProperty("difficulty_level")
    DifficultyLevel   difficultyLevel,

    @NotNull
    @JsonProperty("trainer_id")
    Long              trainerId,

    @NotNull
    DayOfWeek         weekday,

    @NotNull
    @JsonProperty("start_time")
    LocalTime         startTime,

    @NotNull @Positive
    @JsonProperty("duration_minutes")
    Short             durationMinutes,

    @NotNull @Positive
    @JsonProperty("max_capacity")
    Short             maxCapacity
)
{
}
