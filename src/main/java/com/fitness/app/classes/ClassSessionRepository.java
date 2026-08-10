package com.fitness.app.classes;

import com.fitness.app.classes.model.ClassSession;
import com.fitness.app.classes.model.Discipline;
import com.fitness.app.classes.model.EnrollmentStatus;
import com.fitness.app.classes.model.WaitlistStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long>
{
    boolean existsByGroupClass_GroupClassIdAndSessionDate(Long groupClassId, LocalDate sessionDate);

    /** Reschedule conflict check: the same trainer already booked at that date and time, on a different session. */
    boolean existsByTrainerIdAndSessionDateAndStartTimeAndClassSessionIdNot(Long trainerId, LocalDate sessionDate,
                                                                            LocalTime startTime, Long classSessionId);

    @Query("""
           SELECT cs
             FROM ClassSession cs
             JOIN FETCH cs.groupClass g
            WHERE g.groupClassId = :groupClassId
              AND cs.sessionDate BETWEEN :from AND :to
            ORDER BY cs.sessionDate, cs.startTime
           """)
    Page<ClassSession> findByGroupClass(Long groupClassId, LocalDate from, LocalDate to, Pageable pageable);

    @Query("""
           SELECT cs
             FROM ClassSession cs
             JOIN FETCH cs.groupClass g
            WHERE cs.classSessionId = :classSessionId
           """)
    Optional<ClassSession> findDetailById(Long classSessionId);

    /**
     * The billboard: "incluye cupos ocupados y disponibles calculados" (§3.6). hasSeats
     * is resolved with two boolean flags instead of a nullable Boolean directly, the
     * same reason FacilityVisitRepository.search splits openOnly/closedOnly: PostgreSQL
     * cannot infer the type of a bare `:param IS NULL` with no other typed usage.
     *
     * Seats taken is never stored (04-Base-de-Datos §6): the correlated subqueries
     * repeat exactly the two-part sum of the module's design decision #2 (active
     * enrolments + reserved-but-unexpired waitlist notifications).
     */
    @Query("""
           SELECT cs
             FROM ClassSession cs
             JOIN FETCH cs.groupClass g
            WHERE cs.sessionDate BETWEEN :from AND :to
              AND (:discipline IS NULL OR g.discipline = :discipline)
              AND (:trainerId IS NULL OR cs.trainerId = :trainerId)
              AND (:groupClassId IS NULL OR g.groupClassId = :groupClassId)
              AND CASE
                    WHEN :openSeatsOnly = true THEN
                         cs.maxCapacity > (
                             (SELECT COUNT(ce) FROM ClassEnrollment ce
                               WHERE ce.classSessionId = cs.classSessionId AND ce.status <> :cancelledEnrollment)
                           + (SELECT COUNT(w) FROM WaitlistEntry w
                               WHERE w.classSessionId = cs.classSessionId AND w.status = :notifiedWaitlist
                                 AND w.confirmationDeadline >= :now))
                    WHEN :fullOnly = true THEN
                         cs.maxCapacity <= (
                             (SELECT COUNT(ce) FROM ClassEnrollment ce
                               WHERE ce.classSessionId = cs.classSessionId AND ce.status <> :cancelledEnrollment)
                           + (SELECT COUNT(w) FROM WaitlistEntry w
                               WHERE w.classSessionId = cs.classSessionId AND w.status = :notifiedWaitlist
                                 AND w.confirmationDeadline >= :now))
                    ELSE true
                  END
            ORDER BY cs.sessionDate, cs.startTime
           """)
    Page<ClassSession> search(LocalDate from, LocalDate to, Discipline discipline, Long trainerId, Long groupClassId,
                              boolean openSeatsOnly, boolean fullOnly,
                              EnrollmentStatus cancelledEnrollment, WaitlistStatus notifiedWaitlist, Instant now,
                              Pageable pageable);
}
