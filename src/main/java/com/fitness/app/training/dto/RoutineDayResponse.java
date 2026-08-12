package com.fitness.app.training.dto;

import java.time.DayOfWeek;
import java.util.List;

public record RoutineDayResponse(
    DayOfWeek                  weekday,
    List<RoutineExerciseResponse> exercises
)
{
}