package com.fitness.app.nutrition.repository;

import com.fitness.app.nutrition.model.NutritionGoal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

public interface NutritionGoalRepository extends JpaRepository<NutritionGoal, Long>
{
    /** Obtiene la meta activa del socio, que es la que aún no tiene fecha de cierre. */
    Optional<NutritionGoal> findByMemberIdAndEndDateIsNull(Long memberId);

    /** Cierra la meta actual para dejar abierta una nueva versión con la fecha de hoy. */
    @Modifying
    @Query("UPDATE NutritionGoal g SET g.endDate = :today WHERE g.memberId = :memberId AND g.endDate IS NULL")
    int closeCurrent(@Param("memberId") Long memberId, @Param("today") LocalDate today);

}