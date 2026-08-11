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
     * "Catálogo. Filtros: muscle_group, search, active" (§3.7). search must never be
     * null, only empty (PostgreSQL types an untyped null as bytea, like MemberRepository).
     * ExerciseService does the normalization.
     */
    @Query("""
           SELECT e
             FROM Exercise e
            WHERE (:muscleGroup IS NULL OR e.muscleGroup = :muscleGroup)
              AND (:active IS NULL OR e.active = :active)
              AND (:search = '' OR LOWER(e.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                 OR LOWER(e.code) LIKE LOWER(CONCAT('%', :search, '%')))
           """)
    Page<Exercise> search(MuscleGroup muscleGroup, Boolean active, String search, Pageable pageable);
}