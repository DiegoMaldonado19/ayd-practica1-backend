package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.MealType;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Respuesta con la comida y sus totales nutricionales ya calculados. */
public record MealResponse(
    @JsonProperty("meal_id") Long mealId,
    @JsonProperty("member_id") Long memberId,
    @JsonProperty("log_date") LocalDate logDate,
    @JsonProperty("meal_type") MealType mealType,
    @JsonProperty("notes") String notes,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    @JsonProperty("items") List<MealItemResponse> items,
    @JsonProperty("total_calories") BigDecimal totalCalories,
    @JsonProperty("total_protein_g") BigDecimal totalProteinG,
    @JsonProperty("total_carbohydrates_g") BigDecimal totalCarbohydratesG,
    @JsonProperty("total_fat_g") BigDecimal totalFatG
)
{
}