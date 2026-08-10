package com.fitness.app.classes.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/**
 * The recurring class definition the administrator configures once: name, discipline,
 * trainer, weekly slot, duration and cupo. Dated occurrences are class_session, never
 * this table (04-Base-de-Datos §6).
 *
 * trainerId is a plain Long and not a @ManyToOne: Trainer belongs to directory, and
 * 02-Modulos §1 forbids mapping another module's entity.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class GroupClass
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long             groupClassId;

    private String           code;
    private String           name;

    @Enumerated(EnumType.STRING)
    private Discipline       discipline;

    @Enumerated(EnumType.STRING)
    private DifficultyLevel  difficultyLevel;

    private Long             trainerId;

    @Enumerated(EnumType.STRING)
    private DayOfWeek        weekday;

    private LocalTime        startTime;

    // SMALLINT in the DDL: mapping either as int would fail ddl-auto=validate
    // (already documented on Trainer.maxMemberLoad).
    private short             durationMinutes;
    private short             maxCapacity;

    private boolean          active;
    private Instant          createdAt;
}
