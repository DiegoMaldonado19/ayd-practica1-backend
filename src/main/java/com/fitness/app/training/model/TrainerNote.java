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

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TrainerNote
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long            trainerNoteId;

    private Long            memberId;
    private Long            trainerId;

    @Enumerated(EnumType.STRING)
    private TrainerNoteType noteType;

    private String          content;
    private LocalDate       referenceDate;
    private Instant         createdAt;
}