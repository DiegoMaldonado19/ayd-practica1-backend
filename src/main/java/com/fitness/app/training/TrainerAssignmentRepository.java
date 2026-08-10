package com.fitness.app.training;

import com.fitness.app.training.model.TrainerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
