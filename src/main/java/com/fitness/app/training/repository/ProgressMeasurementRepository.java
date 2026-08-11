package com.fitness.app.training.repository;

import com.fitness.app.training.model.ProgressMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ProgressMeasurementRepository extends JpaRepository<ProgressMeasurement, Long>
{
    /** uq_measure_date in Java: one measurement per member per date. */
    boolean existsByMemberIdAndMeasuredOn(Long memberId, LocalDate measuredOn);

    /** The same guard for a correction: any date but the measurement being edited. */
    boolean existsByMemberIdAndMeasuredOnAndProgressMeasurementIdNot(Long memberId, LocalDate measuredOn,
                                                                    Long progressMeasurementId);

    /** "Base de la gráfica de evolución. Filtros: from, to" (§3.7): always oldest first. */
    @Query("""
           SELECT m
             FROM ProgressMeasurement m
            WHERE m.memberId = :memberId
              AND (:from IS NULL OR m.measuredOn >= :from)
              AND (:to   IS NULL OR m.measuredOn <= :to)
            ORDER BY m.measuredOn ASC
           """)
    List<ProgressMeasurement> findByMemberAndRange(Long memberId, LocalDate from, LocalDate to);
}