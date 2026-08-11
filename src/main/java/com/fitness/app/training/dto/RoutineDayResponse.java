package com.fitness.app.training.dto;

import java.time.DayOfWeek;
import java.util.List;

/** One weekday of the routine, with its exercises in display_order. */
public record RoutineDayResponse(
    DayOfWeek                  weekday,
    List<RoutineExerciseResponse> exercises
)
{
}