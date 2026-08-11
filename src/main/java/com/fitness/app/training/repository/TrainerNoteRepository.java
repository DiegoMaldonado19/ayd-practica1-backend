package com.fitness.app.training.repository;

import com.fitness.app.training.model.TrainerNote;
import com.fitness.app.training.model.TrainerNoteType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TrainerNoteRepository extends JpaRepository<TrainerNote, Long>
{
    /** "Observaciones del entrenador. Filtro: note_type" (§3.7): newest first. */
    @Query("""
           SELECT n
             FROM TrainerNote n
            WHERE n.memberId = :memberId
              AND (:noteType IS NULL OR n.noteType = :noteType)
            ORDER BY n.createdAt DESC
           """)
    List<TrainerNote> findByMember(Long memberId, TrainerNoteType noteType);
}