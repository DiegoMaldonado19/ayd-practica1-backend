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

/**
 * A member's personal routine. The archived ones are the history: uq_routine_published
 * keeps at most one PUBLISHED per member, which is the routine in force.
 *
 * memberId and trainerId are plain Longs because Member and Trainer belong to directory
 * and the isolation rule forbids navigating there through JPA. The exercises are this
 * module's own table, so they travel as a real @OneToMany with orphan removal.
 */
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