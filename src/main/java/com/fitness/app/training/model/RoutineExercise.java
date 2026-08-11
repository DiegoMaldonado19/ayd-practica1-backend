package com.fitness.app.training.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;

/**
 * An exercise of a routine, for one suggested day. weekday lives here instead of in a
 * routine_day table because that table would hold nothing but the day of the week
 * (schema.sql). Both relationships stay inside this module, so they are real @ManyToOne.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class RoutineExercise
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long      routineExerciseId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "routine_id")
    private Routine   routine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id")
    private Exercise  exercise;

    @Enumerated(EnumType.STRING)
    private DayOfWeek weekday;

    private short     displayOrder;
    private short     sets;
    private short     repetitions;
    private Short     restSeconds;
    private String    notes;
}