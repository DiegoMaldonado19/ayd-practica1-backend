package com.fitness.app.classes;

import com.fitness.app.classes.model.WaitlistEntry;
import com.fitness.app.classes.model.WaitlistStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WaitlistEntryRepository extends JpaRepository<WaitlistEntry, Long>
{
    /** uq_waitlist_member has no partial predicate either: same reactivation rule as enrolments. */
    Optional<WaitlistEntry> findByClassSessionIdAndMemberId(Long classSessionId, Long memberId);

    /**
     * The reservation half of the seats taken: a NOTIFIED entry counts as occupied until
     * its deadline lapses. Grouped for a whole page of sessions in one query.
     */
    @Query("""
           SELECT w.classSessionId, COUNT(w)
             FROM WaitlistEntry w
            WHERE w.classSessionId IN :classSessionIds
              AND w.status = :status
              AND w.confirmationDeadline >= :now
            GROUP BY w.classSessionId
           """)
    List<Object[]> countReservedByClassSessionIds(Collection<Long> classSessionIds, WaitlistStatus status, Instant now);

    /** Candidates for promotion, ordered by request time; the service re-sorts by plan tier first. */
    List<WaitlistEntry> findByClassSessionIdAndStatusOrderByRequestedAtAsc(Long classSessionId, WaitlistStatus status);

    /** The pending queue (waiting or already notified) shown by GET .../waitlist-entries. */
    List<WaitlistEntry> findByClassSessionIdAndStatusInOrderByRequestedAtAsc(Long classSessionId,
                                                                             Collection<WaitlistStatus> statuses);

    /**
     * The member's own pending queue, for GET /members/{id}/waitlist-entries. A member
     * cannot read the session queue -that one is staff only- so this is the only way they
     * reach the waitlistEntryId that POST .../confirmations needs.
     */
    List<WaitlistEntry> findByMemberIdAndStatusInOrderByRequestedAtAsc(Long memberId,
                                                                       Collection<WaitlistStatus> statuses);

    /** Stale reservations refreshWaitlist must expire before promoting the next member. */
    List<WaitlistEntry> findByClassSessionIdAndStatusAndConfirmationDeadlineBefore(Long classSessionId,
                                                                                   WaitlistStatus status, Instant now);
}
