package com.fitness.app.training.repository;

import com.fitness.app.training.model.Exercise;
import com.fitness.app.training.model.MuscleGroup;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExerciseRepository extends JpaRepository<Exercise, Long>
{
    boolean existsByCode(String code);

    /**
     * Catálogo: busca ejercicios por grupo muscular, texto y estado.
     * `search` nunca debe ser null (vacío permitido).
     */
    @Query("""
           SELECT e
         FROM Exercise e
        WHERE e.muscleGroup = COALESCE(:muscleGroup, e.muscleGroup)
          AND e.active = COALESCE(:active, e.active)
          AND (:search = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%'))
                             OR LOWER(e.code) LIKE LOWER(CONCAT('%', :search, '%')))
           """)
    Page<Exercise> search(MuscleGroup muscleGroup, Boolean active, String search, Pageable pageable);
}