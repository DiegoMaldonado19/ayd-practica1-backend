package com.fitness.app.classes.dto;

import com.fitness.app.classes.model.DifficultyLevel;
import com.fitness.app.classes.model.Discipline;
import com.fitness.app.classes.model.GroupClass;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/** The recurring class definition as the interface sees it. */
public record GroupClassResponse(
    @JsonProperty("group_class_id")
    Long             groupClassId,

    String           code,
    String           name,
    Discipline       discipline,

    @JsonProperty("difficulty_level")
    DifficultyLevel  difficultyLevel,

    @JsonProperty("trainer_id")
    Long             trainerId,

    DayOfWeek        weekday,

    @JsonProperty("start_time")
    LocalTime        startTime,

    @JsonProperty("duration_minutes")
    short            durationMinutes,

    @JsonProperty("max_capacity")
    short            maxCapacity,

    boolean          active,

    @JsonProperty("created_at")
    Instant          createdAt
)
{
    public static GroupClassResponse from(GroupClass groupClass)
    {
        return new GroupClassResponse(
            groupClass.getGroupClassId(),
            groupClass.getCode(),
            groupClass.getName(),
            groupClass.getDiscipline(),
            groupClass.getDifficultyLevel(),
            groupClass.getTrainerId(),
            groupClass.getWeekday(),
            groupClass.getStartTime(),
            groupClass.getDurationMinutes(),
            groupClass.getMaxCapacity(),
            groupClass.isActive(),
            groupClass.getCreatedAt()
        );
    }
}
