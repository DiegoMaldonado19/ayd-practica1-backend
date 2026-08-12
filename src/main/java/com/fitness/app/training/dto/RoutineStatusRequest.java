package com.fitness.app.training.dto;

import com.fitness.app.training.model.RoutineStatus;
import jakarta.validation.constraints.NotNull;

/** PATCH /routines/{id}/status: publica o archiva la rutina */
public record RoutineStatusRequest(
    @NotNull
    RoutineStatus status
)
{
}