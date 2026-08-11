package com.fitness.app.training.repository;

import com.fitness.app.training.model.TrainerAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Every query rides on "end_date IS NULL", which is what uq_assign_current and
 * ix_assign_trainer_current index: the open row is the relationship in force and the
 * closed ones are the history.
 */
public interface TrainerAssignmentRepository extends JpaRepository<TrainerAssignment, Long>
{
    Optional<TrainerAssignment> findByMemberIdAndEndDateIsNull(Long memberId);

    List<TrainerAssignment> findByTrainerIdAndEndDateIsNull(Long trainerId);

    /** The current caseload of a trainer, counted and never stored (04-Base-de-Datos). */
    long countByTrainerIdAndEndDateIsNull(Long trainerId);

    boolean existsByTrainerIdAndMemberIdAndEndDateIsNull(Long trainerId, Long memberId);

    /**
     * "Listado y cartera de un entrenador. Filtros: member_id, trainer_id, active" (§3.7).
     * active=TRUE -> open rows (end_date IS NULL); active=FALSE -> the history. A null
     * Boolean means no filter, so the ternary plays both roles in one predicate.
     */
    @Query("""
           SELECT a
             FROM TrainerAssignment a
            WHERE (:memberId IS NULL OR a.memberId = :memberId)
              AND (:trainerId IS NULL OR a.trainerId = :trainerId)
              AND (:active IS NULL OR (:active = TRUE AND a.endDate IS NULL)
                                     OR (:active = FALSE AND a.endDate IS NOT NULL))
           """)
    Page<TrainerAssignment> search(Long memberId, Long trainerId, Boolean active, Pageable pageable);
}