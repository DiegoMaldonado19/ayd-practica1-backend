package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.GoalType;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Payload para definir la meta nutricional del miembro. */
public record NutritionGoalRequest(
    @NotNull @JsonProperty("goal_type")
    GoalType goalType,

    @NotNull @DecimalMin("800") @DecimalMax("8000") @Digits(integer = 4, fraction = 2)
    @JsonProperty("daily_calories")
    BigDecimal dailyCalories,

    @DecimalMin("0") @DecimalMax("50") @Digits(integer = 2, fraction = 2)
    @JsonProperty("tolerance_percent")
    BigDecimal tolerancePercent,

    @DecimalMin("0.01") @Digits(integer = 3, fraction = 2)
    @JsonProperty("target_weight_kg")
    BigDecimal targetWeightKg
)
{
}