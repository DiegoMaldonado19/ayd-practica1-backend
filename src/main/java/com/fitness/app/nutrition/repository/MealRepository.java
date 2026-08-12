package com.fitness.app.nutrition.repository;

import com.fitness.app.nutrition.model.Meal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface MealRepository extends JpaRepository<Meal, Long>
{
    /** Busca comidas por socio y rango de fechas con orden cronológico. */
    @Query("""
           SELECT m
             FROM Meal m
            WHERE (:memberId IS NULL OR m.memberId = :memberId)
              AND m.logDate BETWEEN :from AND :to
            ORDER BY m.logDate DESC, m.mealId DESC
           """)
    Page<Meal> search(Long memberId, LocalDate from, LocalDate to, Pageable pageable);

    List<Meal> findByMemberIdAndLogDateOrderByMealId(Long memberId, LocalDate logDate);

    List<Meal> findByMemberIdAndLogDateBetweenOrderByLogDateAscMealIdAsc(Long memberId,
                                                                         LocalDate from,
                                                                         LocalDate to);
}