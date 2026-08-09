package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.EmployeeStatus;
import jakarta.validation.constraints.NotNull;

/** Suspension or termination. TERMINATED also deactivates the trainer profile. */
public record EmployeeStatusRequest(@NotNull EmployeeStatus status)
{
}
