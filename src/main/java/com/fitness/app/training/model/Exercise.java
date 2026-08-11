package com.fitness.app.training.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Catalog row the trainer picks from when building a routine. It is a table (and not
 * free text) so the same exercise is named identically across routines and can be
 * reported on (schema.sql).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Exercise
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long        exerciseId;

    private String      code;
    private String      name;

    @Enumerated(EnumType.STRING)
    private MuscleGroup muscleGroup;

    private String      description;
    private String      videoUrl;
    private boolean     active;
}