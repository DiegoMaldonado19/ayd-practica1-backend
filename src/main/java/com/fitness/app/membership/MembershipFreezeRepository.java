package com.fitness.app.membership;

import com.fitness.app.membership.model.MembershipFreeze;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipFreezeRepository extends JpaRepository<MembershipFreeze, Long>
{
    /** In progress means reactivated_on is null, which uq_freeze_in_progress keeps unique. */
    Optional<MembershipFreeze> findByMembershipIdAndReactivatedOnIsNull(Long membershipId);

    /**
     * Every freeze of the contract, newest first. The cycle counters are computed in
     * Java over this list instead of with a second aggregate query: a contract
     * accumulates a handful of freezes, not thousands.
     */
    List<MembershipFreeze> findByMembershipIdOrderByStartDateDesc(Long membershipId);
}
