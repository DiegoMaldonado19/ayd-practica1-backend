package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.MealType;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/** Payload para registrar una comida con sus alimentos y cantidades. */
public record MealRequest(
    @JsonProperty("member_id")
    Long memberId,

    @PastOrPresent
    @JsonProperty("log_date")
    LocalDate logDate,

    @NotNull
    @JsonProperty("meal_type")
    MealType mealType,

    @Size(max = 200)
    String notes,

    @NotEmpty @Valid
    List<MealItemRequest> items
)
{
}