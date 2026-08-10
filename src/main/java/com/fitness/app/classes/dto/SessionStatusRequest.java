package com.fitness.app.classes.dto;

import com.fitness.app.classes.model.SessionStatus;
import jakarta.validation.constraints.NotNull;

/** "El entrenador inicia (IN_PROGRESS) y cierra (COMPLETED) su clase" (§3.6). */
public record SessionStatusRequest(@NotNull SessionStatus status)
{
}
