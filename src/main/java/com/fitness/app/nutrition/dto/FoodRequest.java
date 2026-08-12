package com.fitness.app.nutrition.dto;

import com.fitness.app.nutrition.model.FoodCategory;
import com.fitness.app.nutrition.model.ServingUnit;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Payload para crear o actualizar un alimento del catálogo. */
public record FoodRequest(
    @NotBlank @Size(max = 30)
    String code,

    @NotBlank @Size(max = 80)
    String name,

    @NotNull
    FoodCategory category,

    @NotNull @DecimalMin("0.01") @Digits(integer = 5, fraction = 2)
    @JsonProperty("serving_size")
    BigDecimal servingSize,

    @NotNull
    @JsonProperty("serving_unit")
    ServingUnit servingUnit,

    @NotNull @DecimalMin("0.00") @Digits(integer = 5, fraction = 2)
    BigDecimal calories,

    @NotNull @DecimalMin("0.00") @Digits(integer = 4, fraction = 2)
    @JsonProperty("protein_g")
    BigDecimal proteinG,

    @NotNull @DecimalMin("0.00") @Digits(integer = 4, fraction = 2)
    @JsonProperty("carbohydrates_g")
    BigDecimal carbohydratesG,

    @NotNull @DecimalMin("0.00") @Digits(integer = 4, fraction = 2)
    @JsonProperty("fat_g")
    BigDecimal fatG
)
{
}