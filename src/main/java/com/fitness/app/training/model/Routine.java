package com.fitness.app.training.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Routine
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long    routineId;

    private Long    memberId;
    private Long    trainerId;
    private String  name;
    private String  goalSummary;

    @Enumerated(EnumType.STRING)
    private RoutineStatus status;

    private LocalDate startDate;
    private LocalDate endDate;
    private Instant   createdAt;
    private Instant   updatedAt;

    @OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RoutineExercise> exercises = new ArrayList<>();
}