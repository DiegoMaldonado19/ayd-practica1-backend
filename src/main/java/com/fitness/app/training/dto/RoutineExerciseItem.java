package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;

/** One exercise of a routine: the exercise, the suggested day and the prescription. */
public record RoutineExerciseItem(
    @NotNull
    @JsonProperty("exercise_id")
    Long     exerciseId,

    @NotNull
    DayOfWeek weekday,

    @NotNull @Positive
    @JsonProperty("display_order")
    Short    displayOrder,

    @NotNull @Positive
    Short    sets,

    @NotNull @Positive
    Short    repetitions,

    @Min(0)
    @JsonProperty("rest_seconds")
    Short    restSeconds,

    @Size(max = 200)
    String   notes
)
{
}