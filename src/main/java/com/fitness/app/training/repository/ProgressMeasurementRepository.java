package com.fitness.app.training.repository;

import com.fitness.app.training.model.ProgressMeasurement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface ProgressMeasurementRepository extends JpaRepository<ProgressMeasurement, Long>
{
    boolean existsByMemberIdAndMeasuredOn(Long memberId, LocalDate measuredOn);

    boolean existsByMemberIdAndMeasuredOnAndProgressMeasurementIdNot(Long memberId, LocalDate measuredOn,
                                                                    Long progressMeasurementId);

    /** "Base de la gráfica de evolución. Filtros: from, to" (§3.7): always oldest first. */
    @Query("""
           SELECT m
              FROM ProgressMeasurement m
            WHERE m.memberId = :memberId
              AND m.measuredOn >= COALESCE(:from, m.measuredOn)
              AND m.measuredOn <= COALESCE(:to,   m.measuredOn)
            ORDER BY m.measuredOn ASC
           """)
    List<ProgressMeasurement> findByMemberAndRange(Long memberId, LocalDate from, LocalDate to);
}