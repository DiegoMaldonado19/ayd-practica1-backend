package com.fitness.app.training.dto;

import com.fitness.app.training.model.Exercise;
import com.fitness.app.training.model.MuscleGroup;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The exercise as the interface sees it. */
public record ExerciseResponse(
    @JsonProperty("exercise_id")
    Long             exerciseId,

    String           code,
    String           name,

    @JsonProperty("muscle_group")
    MuscleGroup      muscleGroup,

    String           description,

    @JsonProperty("video_url")
    String           videoUrl,

    boolean          active
)
{
    public static ExerciseResponse from(Exercise exercise)
    {
        return new ExerciseResponse(exercise.getExerciseId(),
                                    exercise.getCode(),
                                    exercise.getName(),
                                    exercise.getMuscleGroup(),
                                    exercise.getDescription(),
                                    exercise.getVideoUrl(),
                                    exercise.isActive());
    }
}