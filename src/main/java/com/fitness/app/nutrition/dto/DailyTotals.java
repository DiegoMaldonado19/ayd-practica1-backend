package com.fitness.app.nutrition.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/** Totales derivados de un día o de un momento de comida. */
public record DailyTotals(
    @JsonProperty("calories")          BigDecimal calories,
    @JsonProperty("protein_g")         BigDecimal proteinG,
    @JsonProperty("carbohydrates_g")   BigDecimal carbohydratesG,
    @JsonProperty("fat_g")             BigDecimal fatG
)
{
}