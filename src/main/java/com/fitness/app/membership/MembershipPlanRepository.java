package com.fitness.app.membership;

import com.fitness.app.membership.model.MembershipPlan;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, Long>
{
    boolean existsByCode(String code);

    /** One query with a nullable filter: null brings the whole catalog, active and not. */
    @Query("""
           SELECT p
             FROM MembershipPlan p
            WHERE (:active IS NULL OR p.active = :active)
           """)
    Page<MembershipPlan> search(Boolean active, Pageable pageable);
}
