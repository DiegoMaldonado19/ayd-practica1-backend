package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.CalorieStatus;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Resumen nutricional del día con totales, tiempos de comida y comparación con la meta. */
public record NutritionSummaryResponse(
    @JsonProperty("member_id")          Long                    memberId,
    @JsonProperty("date")               LocalDate               date,
    @JsonProperty("totals")             DailyTotals             totals,
    @JsonProperty("by_meal_time")       List<MealTimeSummary>   byMealTime,
    @JsonProperty("goal")               NutritionGoalResponse   goal,
    @JsonProperty("calorie_status")     CalorieStatus           calorieStatus,
    @JsonProperty("calorie_difference") BigDecimal              calorieDifference,
    @JsonProperty("percent_of_goal")    BigDecimal              percentOfGoal
)
{
}