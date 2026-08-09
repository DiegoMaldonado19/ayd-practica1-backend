package com.fitness.app.access;

import com.fitness.app.access.model.GuestPass;
import com.fitness.app.access.model.GuestPassType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;

public interface GuestPassRepository extends JpaRepository<GuestPass, Long>
{
    /** Count free trial passes of a person: the tope is 1 per person ever. */
    long countByPersonIdAndPassType(Long personId, GuestPassType passType);

    /**
     * Search with nullable filters. from and to are passed as Instant with
     * sentinel values for the same typing reason as FacilityVisitRepository.
     */
    @Query("""
           SELECT g
             FROM GuestPass g
            WHERE (g.checkedInAt >= :from)
              AND (g.checkedInAt < :to)
              AND (:passType IS NULL OR g.passType = :passType)
           ORDER BY g.checkedInAt DESC
           """)
    Page<GuestPass> search(Instant from, Instant to, GuestPassType passType, Pageable pageable);
}
