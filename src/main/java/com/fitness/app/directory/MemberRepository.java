package com.fitness.app.directory;

import com.fitness.app.directory.model.Member;
import com.fitness.app.directory.model.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MemberRepository extends JpaRepository<Member, Long>
{
    boolean existsByPerson_PersonId(Long personId);

    /**
     * One query with nullable filters instead of a Specification and its helper
     * classes. The JOIN FETCH is what keeps a page of members at one query: the
     * response carries the person block, and Person is lazy.
     *
     * search must never be null, only empty: PostgreSQL types an untyped null
     * parameter as bytea and lower(bytea) does not exist. An empty term yields
     * LIKE '%%', which matches everything. MemberService does the normalization.
     *
     * memberIds resolves the plan_code and membership_status filters of §3.2, which
     * membership answers because those columns are not directory's. It travels with a
     * flag instead of a null because JPQL cannot bind a null list into an IN.
     */
    @Query("""
           SELECT m
             FROM Member m
             JOIN FETCH m.person p
            WHERE (:status IS NULL OR m.status = :status)
              AND (:filterByMembership = FALSE OR m.memberId IN :memberIds)
              AND (LOWER(p.firstName)      LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.lastName)       LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.documentNumber) LIKE LOWER(CONCAT('%', :search, '%')))
           """)
    Page<Member> search(MemberStatus status,
                        boolean      filterByMembership,
                        List<Long>   memberIds,
                        String       search,
                        Pageable     pageable);
}
