package com.fitness.app.training.dto;

import com.fitness.app.training.model.RoutineExercise;
import com.fasterxml.jackson.annotation.JsonProperty;

public record RoutineExerciseResponse(
    @JsonProperty("routine_exercise_id")
    Long     routineExerciseId,

    @JsonProperty("exercise_id")
    Long     exerciseId,

    @JsonProperty("exercise_code")
    String   exerciseCode,

    @JsonProperty("exercise_name")
    String   exerciseName,

    @JsonProperty("display_order")
    short    displayOrder,

    short    sets,
    short    repetitions,

    @JsonProperty("rest_seconds")
    Short    restSeconds,

    String   notes
)
{
    public static RoutineExerciseResponse from(RoutineExercise exercise)
    {
        return new RoutineExerciseResponse(exercise.getRoutineExerciseId(),
                                           exercise.getExercise().getExerciseId(),
                                           exercise.getExercise().getCode(),
                                           exercise.getExercise().getName(),
                                           exercise.getDisplayOrder(),
                                           exercise.getSets(),
                                           exercise.getRepetitions(),
                                           exercise.getRestSeconds(),
                                           exercise.getNotes());
    }
}