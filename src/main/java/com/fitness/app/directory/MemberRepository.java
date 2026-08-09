package com.fitness.app.directory;

import com.fitness.app.directory.model.Member;
import com.fitness.app.directory.model.MemberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
     */
    @Query("""
           SELECT m
             FROM Member m
             JOIN FETCH m.person p
            WHERE (:status IS NULL OR m.status = :status)
              AND (LOWER(p.firstName)      LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.lastName)       LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(p.documentNumber) LIKE LOWER(CONCAT('%', :search, '%')))
           """)
    Page<Member> search(MemberStatus status, String search, Pageable pageable);
}
