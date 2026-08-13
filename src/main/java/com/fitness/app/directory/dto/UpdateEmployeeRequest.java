package com.fitness.app.directory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.time.LocalDate;

/**
 * Personal data and hire date of an existing employee.
 *
 * position is deliberately absent: moving someone into TRAINER would have to
 * create their trainer row and moving them out would have to decide what happens
 * to their caseload. That is a re-hire, not an edit. The trainer profile has its
 * own endpoint (PUT /trainers/{id}) and the lifecycle has PATCH /{id}/status.
 */
public record UpdateEmployeeRequest(@NotNull @Valid          PersonRequestDTO person,
                                    @NotNull @PastOrPresent  LocalDate        hiredOn)
{
}
