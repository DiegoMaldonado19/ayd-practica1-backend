package com.fitness.app.training.repository;

import com.fitness.app.training.model.RoutineExercise;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoutineExerciseRepository extends JpaRepository<RoutineExercise, Long>
{
    /**
     * Borra en lote las filas de ejercicios asociadas a una rutina (usado en reemplazo).
     *
     * @param routineId id de la rutina cuyas filas se borrarán
     */
    void deleteByRoutine_RoutineId(Long routineId);
}