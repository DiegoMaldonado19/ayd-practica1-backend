package com.fitness.app.training.dto;

import com.fitness.app.training.model.ProgressMeasurement;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** One dated measurement as the interface sees it. */
public record ProgressMeasurementResponse(
    @JsonProperty("progress_measurement_id")
    Long         progressMeasurementId,

    @JsonProperty("member_id")
    Long         memberId,

    @JsonProperty("trainer_id")
    Long         trainerId,

    @JsonProperty("measured_on")
    LocalDate    measuredOn,

    @JsonProperty("weight_kg")
    BigDecimal   weightKg,

    @JsonProperty("waist_cm")
    BigDecimal   waistCm,

    @JsonProperty("arm_cm")
    BigDecimal   armCm,

    @JsonProperty("leg_cm")
    BigDecimal   legCm,

    @JsonProperty("body_fat_percent")
    BigDecimal   bodyFatPercent,

    String       notes,

    @JsonProperty("created_at")
    Instant      createdAt
)
{
    public static ProgressMeasurementResponse from(ProgressMeasurement measurement)
    {
        return new ProgressMeasurementResponse(measurement.getProgressMeasurementId(),
                                               measurement.getMemberId(),
                                               measurement.getTrainerId(),
                                               measurement.getMeasuredOn(),
                                               measurement.getWeightKg(),
                                               measurement.getWaistCm(),
                                               measurement.getArmCm(),
                                               measurement.getLegCm(),
                                               measurement.getBodyFatPercent(),
                                               measurement.getNotes(),
                                               measurement.getCreatedAt());
    }
}