package com.fitness.app.nutrition.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Representa una línea dentro de una comida con un alimento y la cantidad consumida.
 * Los macros se calculan a partir de la porción del alimento y no se guardan aquí.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MealItem
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long       mealItemId;

    private Long       mealId;
    private Long       foodId;
    private BigDecimal quantity;
}