package com.fitness.app.membership;

import com.fitness.app.membership.model.Membership;
import com.fitness.app.membership.model.MembershipStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, Long>
{
    /** Most recent contract of a member, needed by findActiveOrFail. */
    Optional<Membership> findFirstByMemberIdOrderByMembershipIdDesc(Long memberId);

    boolean existsByMemberIdAndStatusIn(Long memberId, Collection<MembershipStatus> statuses);

    /**
     * One query with nullable filters instead of a Specification and its helper
     * classes. The JOIN FETCH keeps a page of contracts at one query: the response
     * carries the plan block with its benefits, and plan is lazy.
     *
     * expiringBefore is what "próxima a vencer" becomes: the service turns
     * expiring_in_days into a date, because the state is derived and never stored.
     *
     * It must never be null, only far away: PostgreSQL determines the type of a
     * parameter from its context, and `? IS NULL` gives it none, so the query dies
     * with "could not determine data type". The service sends a date no contract can
     * reach instead, which matches everything. Same reason search is normalized to ""
     * in MemberRepository.
     */
    @Query("""
           SELECT m
             FROM Membership m
             JOIN FETCH m.plan p
            WHERE (:status IS NULL OR m.status = :status)
              AND (:planId IS NULL OR p.membershipPlanId = :planId)
              AND m.endDate <= :expiringBefore
           """)
    Page<Membership> search(MembershipStatus status, Long planId, LocalDate expiringBefore, Pageable pageable);

    /** History of one member, newest contract first. */
    @Query("""
           SELECT m
             FROM Membership m
             JOIN FETCH m.plan p
            WHERE m.memberId = :memberId
            ORDER BY m.membershipId DESC
           """)
    Page<Membership> findHistory(Long memberId, Pageable pageable);

    /**
     * The member ids whose *current* contract matches, which is what the plan_code and
     * membership_status filters of §3.2 mean. Without the NOT EXISTS, asking for
     * EXPIRED would also return whoever already renewed.
     *
     * The newest contract is compared by membershipId and not by startDate because a
     * renewal signed the same day it starts would tie.
     *
     * planCode must never be null, only empty, for the same typing reason as
     * expiringBefore above. No plan carries an empty code, so "" matches everything.
     */
    @Query("""
           SELECT m.memberId
             FROM Membership m
            WHERE (:planCode = '' OR m.plan.code = :planCode)
              AND (:status IS NULL OR m.status = :status)
              AND NOT EXISTS (SELECT 1
                                FROM Membership n
                               WHERE n.memberId = m.memberId
                                 AND n.membershipId > m.membershipId)
           """)
    List<Long> findMemberIdsByCurrentMembership(String planCode, MembershipStatus status);

    /**
     * The nightly expiry. A bulk update instead of loading every due contract: nothing
     * else changes on the row, and the number of rows grows with the gym.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
           UPDATE Membership m
              SET m.status = :expired
            WHERE m.status = :active
              AND m.endDate < :today
           """)
    int expireDueContracts(LocalDate today, MembershipStatus active, MembershipStatus expired);
}
