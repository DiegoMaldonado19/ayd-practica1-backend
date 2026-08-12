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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Guarda la meta nutricional de un miembro en un periodo determinado.
 * La meta activa es la que no tiene fecha de cierre y se reemplaza con una nueva versión.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class NutritionGoal
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long        nutritionGoalId;

    private Long        memberId;

    @Enumerated(EnumType.STRING)
    private GoalType    goalType;

    private BigDecimal  dailyCalories;
    private BigDecimal  tolerancePercent;
    private BigDecimal  targetWeightKg;

    @Enumerated(EnumType.STRING)
    private GoalDefinedBy definedBy;

    private Long        definedByUserId;
    private LocalDate   startDate;
    private LocalDate   endDate;
}