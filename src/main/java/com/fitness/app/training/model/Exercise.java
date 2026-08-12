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