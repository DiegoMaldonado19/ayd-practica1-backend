package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.Specialty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Replaces the whole set of specialties. A Set and not a List: the primary key of
 * trainer_specialty is (trainer_id, specialty), so a repeated value is not a
 * second row, it is a duplicate key.
 *
 * An empty set is valid: it is how a trainer stops being specialized.
 */
public record TrainerSpecialtiesRequest(@NotNull Set<Specialty> specialties)
{
}
