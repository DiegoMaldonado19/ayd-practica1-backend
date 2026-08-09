package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.Specialty;
import com.fitness.app.directory.model.Trainer;

import java.util.Set;

/**
 * The trainer profile with the identity of the employee behind it.
 *
 * The current caseload of §3.2 #15 is not here: it is COUNT(*) over
 * trainer_assignment, a table of training, and directory does not read another
 * module's tables. It arrives with that module.
 */
public record TrainerResponse(Long           trainerId,
                              Long           employeeId,
                              PersonDTO      person,
                              short          maxMemberLoad,
                              String         bio,
                              boolean        active,
                              Set<Specialty> specialties)
{
    public static TrainerResponse from(Trainer trainer)
    {
        return new TrainerResponse(trainer.getTrainerId(),
                                   trainer.getEmployee().getEmployeeId(),
                                   PersonDTO.from(trainer.getEmployee().getPerson()),
                                   trainer.getMaxMemberLoad(),
                                   trainer.getBio(),
                                   trainer.isActive(),
                                   Set.copyOf(trainer.getSpecialties()));
    }
}
