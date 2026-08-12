package com.fitness.app.nutrition.model;

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

/**
 * Registra una comida del día con su tipo, fecha y observaciones.
 * Cada comida pertenece a un miembro y puede contener varios alimentos.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Meal
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long      mealId;

    private Long      memberId;
    private LocalDate logDate;

    @Enumerated(EnumType.STRING)
    private MealType  mealType;

    private String    notes;
    private Instant   createdAt;
    private Instant   updatedAt;
}