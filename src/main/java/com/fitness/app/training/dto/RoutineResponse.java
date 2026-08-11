package com.fitness.app.training.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitness.app.training.model.Routine;
import com.fitness.app.training.model.RoutineExercise;
import com.fitness.app.training.model.RoutineStatus;

public record RoutineResponse(
    @JsonProperty("routine_id")
    Long                     routineId,

    @JsonProperty("member_id")
    Long                     memberId,

    @JsonProperty("trainer_id")
    Long                     trainerId,

    String                   name,

    @JsonProperty("goal_summary")
    String                   goalSummary,

    RoutineStatus            status,

    @JsonProperty("start_date")
    LocalDate                startDate,

    @JsonProperty("end_date")
    LocalDate                endDate,

    @JsonProperty("created_at")
    Instant                  createdAt,

    @JsonProperty("updated_at")
    Instant                  updatedAt,

    List<RoutineDayResponse> days
)
{
    private static final List<DayOfWeek> WEEKDAY_ORDER = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    public static RoutineResponse from(Routine routine)
    {
        var days = new ArrayList<RoutineDayResponse>();

        for (var weekday : WEEKDAY_ORDER)
        {
            var ofTheDay = routine.getExercises().stream()
                    .filter(exercise -> exercise.getWeekday() == weekday)
                    .sorted(Comparator.comparing(RoutineExercise::getDisplayOrder)) // dddd
                    .map(RoutineExerciseResponse::from)
                    .toList();

            if (!ofTheDay.isEmpty())
            {
                days.add(new RoutineDayResponse(weekday, ofTheDay));
            }
        }

        return new RoutineResponse(routine.getRoutineId(),
                                   routine.getMemberId(),
                                   routine.getTrainerId(),
                                   routine.getName(),
                                   routine.getGoalSummary(),
                                   routine.getStatus(),
                                   routine.getStartDate(),
                                   routine.getEndDate(),
                                   routine.getCreatedAt(),
                                   routine.getUpdatedAt(),
                                   days);
    }
}