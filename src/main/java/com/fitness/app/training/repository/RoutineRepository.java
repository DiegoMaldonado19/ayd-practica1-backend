package com.fitness.app.training.repository;

import com.fitness.app.training.model.Routine;
import com.fitness.app.training.model.RoutineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RoutineRepository extends JpaRepository<Routine, Long>
{
    /** The routine in force for a member, for the uq_routine_published swap on publish. */
    Optional<Routine> findByMemberIdAndStatus(Long memberId, RoutineStatus status);

    /** "Listado e histórico de un socio. Filtros: member_id, trainer_id, status" (§3.7). */
    @Query("""
           SELECT r
             FROM Routine r
            WHERE (:memberId IS NULL OR r.memberId = :memberId)
              AND (:trainerId IS NULL OR r.trainerId = :trainerId)
              AND (:status IS NULL OR r.status = :status)
           """)
    Page<Routine> search(Long memberId, Long trainerId, RoutineStatus status, Pageable pageable);
}