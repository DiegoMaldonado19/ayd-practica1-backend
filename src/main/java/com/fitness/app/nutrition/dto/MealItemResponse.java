package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.ServingUnit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Representa un alimento dentro de una comida con sus calorías y macros calculados. */
public record MealItemResponse(
    @JsonProperty("meal_item_id")      Long         mealItemId,
    @JsonProperty("meal_id")           Long         mealId,
    @JsonProperty("food_id")           Long         foodId,
    @JsonProperty("food_name")         String       foodName,
    @JsonProperty("quantity")          BigDecimal   quantity,
    @JsonProperty("serving_size")      BigDecimal   servingSize,
    @JsonProperty("serving_unit")      ServingUnit  servingUnit,
    @JsonProperty("calories")          BigDecimal   calories,
    @JsonProperty("protein_g")         BigDecimal   proteinG,
    @JsonProperty("carbohydrates_g")   BigDecimal   carbohydratesG,
    @JsonProperty("fat_g")             BigDecimal   fatG
)
{
}