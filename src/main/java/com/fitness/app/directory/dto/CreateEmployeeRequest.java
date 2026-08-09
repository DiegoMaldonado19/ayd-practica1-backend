package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.EmployeePosition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Alta de empleado. When position is TRAINER the service also creates the trainer
 * row, which is what maxMemberLoad and bio are for; both are ignored otherwise.
 *
 * maxMemberLoad is a Short and not a short: null means "use the column default of
 * 20" and a primitive could not express that.
 */
public record CreateEmployeeRequest(@NotNull @Valid       PersonRequestDTO person,
                                    @NotNull              EmployeePosition position,
                                    @NotNull              LocalDate        hiredOn,
                                    @Positive @Max(32767) Short            maxMemberLoad,
                                    @Size(max = 500)      String           bio)
{
}
