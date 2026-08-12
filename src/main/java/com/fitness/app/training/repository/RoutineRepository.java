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
    Optional<Routine> findByMemberIdAndStatus(Long memberId, RoutineStatus status);

    /** "Listado e histórico de un socio. Filtros: member_id, trainer_id, status" (§3.7). */
    @Query("""
           SELECT r
            FROM Routine r
            WHERE r.memberId = COALESCE(:memberId, r.memberId)
              AND r.trainerId = COALESCE(:trainerId, r.trainerId)
              AND r.status = COALESCE(:status, r.status)
           """)
    Page<Routine> search(Long memberId, Long trainerId, RoutineStatus status, Pageable pageable);
}