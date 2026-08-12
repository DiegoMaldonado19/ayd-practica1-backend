package com.fitness.app.nutrition.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula la contribución nutricional de una cantidad ingerida
 * respecto a una porción estándar del alimento.
 */
final class NutritionMath
{
    static final BigDecimal HUNDRED = new BigDecimal("100");

    private NutritionMath()
    {
    }

    /** Convierte una cantidad consumida en calorías o macros equivalentes. */
    static BigDecimal contribution(BigDecimal quantity, BigDecimal servingSize, BigDecimal baseValue)
    {
        return baseValue.multiply(quantity)
                .divide(servingSize, 2, RoundingMode.HALF_UP);
    }
}