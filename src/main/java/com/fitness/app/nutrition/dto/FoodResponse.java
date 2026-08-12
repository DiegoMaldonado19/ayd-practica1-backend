package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.Food;
import com.fitness.app.nutrition.model.FoodCategory;
import com.fitness.app.nutrition.model.ServingUnit;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;

/** Respuesta pública con la información de un alimento disponible. */
public record FoodResponse(
    @JsonProperty("food_id") Long foodId,
    @JsonProperty("code") String code,
    @JsonProperty("name") String name,
    @JsonProperty("category") FoodCategory category,
    @JsonProperty("serving_size") BigDecimal servingSize,
    @JsonProperty("serving_unit") ServingUnit servingUnit,
    @JsonProperty("calories")  BigDecimal calories,
    @JsonProperty("protein_g")  BigDecimal proteinG,
    @JsonProperty("carbohydrates_g") BigDecimal carbohydratesG,
    @JsonProperty("fat_g")  BigDecimal fatG,
    @JsonProperty("active")  boolean active,
    @JsonProperty("created_at") Instant createdAt
)
{
    public static FoodResponse from(Food food)
    {
        return new FoodResponse(
            food.getFoodId(),
            food.getCode(),
            food.getName(),
            food.getCategory(),
            food.getServingSize(),
            food.getServingUnit(),
            food.getCalories(),
            food.getProteinG(),
            food.getCarbohydratesG(),
            food.getFatG(),
            food.isActive(),
            food.getCreatedAt()
        );
    }
}