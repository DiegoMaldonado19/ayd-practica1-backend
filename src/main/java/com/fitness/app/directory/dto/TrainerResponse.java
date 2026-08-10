package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.Specialty;
import com.fitness.app.directory.model.Trainer;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * The trainer profile with the identity of the employee behind it.
 * The current caseload comes from the training module's TrainerAssignmentService.
 */
public record TrainerResponse(
        @JsonProperty("trainer_id")
        Long trainerId,

        @JsonProperty("employee_id")
        Long employeeId,

        @JsonProperty("person")
        PersonDTO person,

        @JsonProperty("max_member_load")
        short maxMemberLoad,

        @JsonProperty("assigned_member_count")
        long assignedMemberCount,

        @JsonProperty("bio")
        String bio,

        @JsonProperty("active")
        boolean active,

        @JsonProperty("specialties")
        Set<Specialty> specialties)
{
    public static TrainerResponse from(Trainer trainer)
    {
        return new TrainerResponse(trainer.getTrainerId(),
                                   trainer.getEmployee().getEmployeeId(),
                                   PersonDTO.from(trainer.getEmployee().getPerson()),
                                   trainer.getMaxMemberLoad(),
                                   0,  // will be overridden by from(Trainer, long)
                                   trainer.getBio(),
                                   trainer.isActive(),
                                   Set.copyOf(trainer.getSpecialties()));
    }

    public static TrainerResponse from(Trainer trainer, long caseload)
    {
        return new TrainerResponse(trainer.getTrainerId(),
                                   trainer.getEmployee().getEmployeeId(),
                                   PersonDTO.from(trainer.getEmployee().getPerson()),
                                   trainer.getMaxMemberLoad(),
                                   caseload,
                                   trainer.getBio(),
                                   trainer.isActive(),
                                   Set.copyOf(trainer.getSpecialties()));
    }
}
