package com.fitness.app.classes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalTime;

/** "Reprograma: fecha, hora, cupo o entrenador de esa sesión" (§3.6). */
public record SessionRescheduleRequest(
    @NotNull
    @JsonProperty("session_date")
    LocalDate sessionDate,

    @NotNull
    @JsonProperty("start_time")
    LocalTime startTime,

    @NotNull @Positive
    @JsonProperty("max_capacity")
    Short     maxCapacity,

    @NotNull
    @JsonProperty("trainer_id")
    Long      trainerId
)
{
}
