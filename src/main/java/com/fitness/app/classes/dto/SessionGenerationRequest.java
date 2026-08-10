package com.fitness.app.classes.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** "Genera las sesiones fechadas de un rango" (§3.6): one class_session per matching weekday in [from, to]. */
public record SessionGenerationRequest(
    @NotNull LocalDate from,
    @NotNull LocalDate to
)
{
}
