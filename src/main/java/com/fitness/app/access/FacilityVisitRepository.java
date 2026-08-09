package com.fitness.app.access;

import com.fitness.app.access.model.FacilityVisit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;

public interface FacilityVisitRepository extends JpaRepository<FacilityVisit, Long>
{
    /** Open visit of a member. The unique index uq_visit_open guarantees at most one. */
    Optional<FacilityVisit> findByMemberIdAndCheckedOutAtIsNull(Long memberId);

    /**
     * Search with nullable filters. from and to are passed as Instant (not LocalDate)
     * with sentinel values: Instant.MIN and Instant.MAX, because PostgreSQL type inference
     * fails on IS NULL with temporal types.
     *
     * openOnly and closedOnly drive the filter: if openOnly is true, only open visits;
     * if closedOnly is true, only closed; if both false, all.
     */
    @Query("""
           SELECT v
             FROM FacilityVisit v
            WHERE (:memberId IS NULL OR v.memberId = :memberId)
              AND (v.checkedInAt >= :from)
              AND (v.checkedInAt < :to)
              AND CASE
                    WHEN :openOnly = true  THEN v.checkedOutAt IS NULL
                    WHEN :closedOnly = true THEN v.checkedOutAt IS NOT NULL
                    ELSE true
                  END
           ORDER BY v.checkedInAt DESC
           """)
    Page<FacilityVisit> search(Long memberId, Instant from, Instant to, boolean openOnly, boolean closedOnly, Pageable pageable);

    boolean existsByMemberIdAndCheckedOutAtIsNull(Long memberId);
}
