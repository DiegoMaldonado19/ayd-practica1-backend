package com.fitness.app.training.repository;

import com.fitness.app.training.model.TrainerAlert;
import com.fitness.app.training.model.TrainerAlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TrainerAlertRepository extends JpaRepository<TrainerAlert, Long>
{
    /**
     * "Cola de alertas. Filtro: status" (§3.7). trainerId null means the administrator
     * sees the whole queue; a trainer only their own (TrainerAlertService scopes it).
     */
    @Query("""
           SELECT a
             FROM TrainerAlert a
            WHERE (:trainerId IS NULL OR a.trainerId = :trainerId)
              AND (:status IS NULL OR a.status = :status)
            ORDER BY a.createdAt DESC
           """)
    Page<TrainerAlert> search(Long trainerId, TrainerAlertStatus status, Pageable pageable);
}