package com.fitness.app.nutrition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Línea de una comida con el alimento y la cantidad consumida. */
public record MealItemRequest(
    @NotNull @JsonProperty("food_id")
    Long foodId,

    @NotNull @DecimalMin("0.01") @Digits(integer = 5, fraction = 2)
    BigDecimal quantity
)
{
}