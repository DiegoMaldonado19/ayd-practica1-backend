package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.MealType;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** PUT /meals/{id}: same day only, so the date is not editable. */
public record MealUpdateRequest(
    @NotNull @JsonProperty("meal_type")
    MealType mealType,

    @Size(max = 200)
    String notes,

    @NotEmpty @Valid
    List<MealItemRequest> items
)
{
}