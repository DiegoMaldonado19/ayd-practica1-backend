package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ProgressMeasurementRequest(
    // A measurement is a reading that already happened; a future date is a typo.
    // Backdating stays allowed on purpose, to load a member's history.
    @NotNull @PastOrPresent
    @JsonProperty("measured_on")
    LocalDate    measuredOn,

    @NotNull @DecimalMin("0.01") @Digits(integer = 3, fraction = 2)
    @JsonProperty("weight_kg")
    BigDecimal   weightKg,

    @DecimalMin("0.01") @Digits(integer = 3, fraction = 2)
    @JsonProperty("waist_cm")
    BigDecimal   waistCm,

    @DecimalMin("0.01") @Digits(integer = 3, fraction = 2)
    @JsonProperty("arm_cm")
    BigDecimal   armCm,

    @DecimalMin("0.01") @Digits(integer = 3, fraction = 2)
    @JsonProperty("leg_cm")
    BigDecimal   legCm,

    @DecimalMin("0.00") @DecimalMax("100.00") @Digits(integer = 2, fraction = 2)
    @JsonProperty("body_fat_percent")
    BigDecimal   bodyFatPercent,

    @Size(max = 300)
    String       notes
)
{
}