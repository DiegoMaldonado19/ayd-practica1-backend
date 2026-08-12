package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.MealType;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Resumen de un momento del día dentro del total nutricional. */
public record MealTimeSummary(
    @JsonProperty("meal_type")   MealType     mealType,
    @JsonProperty("meal_count")  long         mealCount,
    @JsonProperty("calories")    BigDecimal   calories,
    @JsonProperty("protein_g")   BigDecimal   proteinG,
    @JsonProperty("carbohydrates_g") BigDecimal carbohydratesG,
    @JsonProperty("fat_g")       BigDecimal   fatG
)
{
}