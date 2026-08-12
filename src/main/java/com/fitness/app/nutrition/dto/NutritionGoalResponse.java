package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.GoalDefinedBy;
import com.fitness.app.nutrition.model.GoalType;
import com.fitness.app.nutrition.model.NutritionGoal;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Respuesta con la meta actual que está vigente para el miembro. */
public record NutritionGoalResponse(
    @JsonProperty("nutrition_goal_id") Long nutritionGoalId,
    @JsonProperty("member_id") Long  memberId,
    @JsonProperty("goal_type") GoalType goalType,
    @JsonProperty("daily_calories") BigDecimal dailyCalories,
    @JsonProperty("tolerance_percent") BigDecimal tolerancePercent,
    @JsonProperty("target_weight_kg") BigDecimal targetWeightKg,
    @JsonProperty("defined_by") GoalDefinedBy  definedBy,
    @JsonProperty("start_date") LocalDate startDate
)
{
    public static NutritionGoalResponse from(NutritionGoal goal)
    {
        return new NutritionGoalResponse(
            goal.getNutritionGoalId(),
            goal.getMemberId(),
            goal.getGoalType(),
            goal.getDailyCalories(),
            goal.getTolerancePercent(),
            goal.getTargetWeightKg(),
            goal.getDefinedBy(),
            goal.getStartDate()
        );
    }
}