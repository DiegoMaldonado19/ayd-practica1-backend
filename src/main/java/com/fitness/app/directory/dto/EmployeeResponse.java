package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.Employee;
import com.fitness.app.directory.model.EmployeePosition;
import com.fitness.app.directory.model.EmployeeStatus;

import java.time.LocalDate;

/**
 * The staff file as the interface sees it.
 *
 * trainerId is null for anyone who is not a trainer: it is the link the interface
 * follows to /trainers/{id} for the load cap and the specialties.
 */
public record EmployeeResponse(Long             employeeId,
                               PersonDTO        person,
                               String           employeeCode,
                               EmployeePosition position,
                               LocalDate        hiredOn,
                               LocalDate        terminatedOn,
                               EmployeeStatus   status,
                               Long             trainerId)
{
    public static EmployeeResponse from(Employee employee)
    {
        return new EmployeeResponse(employee.getEmployeeId(),
                                    PersonDTO.from(employee.getPerson()),
                                    employee.getEmployeeCode(),
                                    employee.getPosition(),
                                    employee.getHiredOn(),
                                    employee.getTerminatedOn(),
                                    employee.getStatus(),
                                    employee.getTrainer() == null ? null
                                                                  : employee.getTrainer().getTrainerId());
    }
}
