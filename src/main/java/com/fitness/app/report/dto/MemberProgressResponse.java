package com.fitness.app.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Progress measurements for a member over time.
 */
public record MemberProgressResponse(
        @JsonProperty("measurement_id")
        Long measurementId,

        @JsonProperty("measured_on")
        LocalDate measuredOn,

        @JsonProperty("weight_kg")
        BigDecimal weightKg,

        @JsonProperty("weight_change_kg")
        BigDecimal weightChangeKg,

        @JsonProperty("body_fat_percent")
        BigDecimal bodyFatPercent,

        @JsonProperty("waist_cm")
        BigDecimal waistCm,

        @JsonProperty("arm_cm")
        BigDecimal armCm,

        @JsonProperty("leg_cm")
        BigDecimal legCm,

        @JsonProperty("notes")
        String notes)
{
}
