package com.fitness.app.training.repository;

import com.fitness.app.training.model.RoutineExercise;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Only the batch delete a routine replacement needs: uq_rtn_ex_order would collide if
 * the cascade diff tried to insert before deleting the old rows, so the PUT deletes and
 * flushes first. RoutineService keeps the cascade for create and orphan cleanup.
 */
public interface RoutineExerciseRepository extends JpaRepository<RoutineExercise, Long>
{
    void deleteByRoutine_RoutineId(Long routineId);
}