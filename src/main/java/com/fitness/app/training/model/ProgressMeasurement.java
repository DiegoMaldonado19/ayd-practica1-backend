package com.fitness.app.training.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Periodic body measurements taken by the trainer. The statement fixes the list of
 * measures, so they are columns; an attribute-value table would be no more normalized,
 * only harder to query and to chart (schema.sql).
 *
 * Each measurement is dated (uq_measure_date), which is what lets the member and the
 * trainer see the evolution over time.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ProgressMeasurement
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long       progressMeasurementId;

    private Long       memberId;
    private Long       trainerId;
    private LocalDate  measuredOn;
    private BigDecimal weightKg;
    private BigDecimal waistCm;
    private BigDecimal armCm;
    private BigDecimal legCm;
    private BigDecimal bodyFatPercent;
    private String     notes;
    private Instant    createdAt;
}