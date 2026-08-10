package com.fitness.app.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

/**
 * Nutrition adherence metrics for members.
 */
public record NutritionAdherenceResponse(
        @JsonProperty("member_name")
        String memberName,

        @JsonProperty("days_logged")
        long daysLogged,

        @JsonProperty("average_daily_calories")
        BigDecimal averageDailyCalories,

        @JsonProperty("nutrition_goal_calories")
        BigDecimal nutritionGoalCalories,

        @JsonProperty("days_within_goal")
        long daysWithinGoal,

        @JsonProperty("adherence_rate")
        Double adherenceRate)
{
}
