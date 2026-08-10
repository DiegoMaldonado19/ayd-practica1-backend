package com.fitness.app.classes;

import com.fitness.app.classes.model.ClassEnrollment;
import com.fitness.app.classes.model.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, Long>
{
    /** uq_enroll_member has no partial predicate: a CANCELLED row is reactivated, never duplicated. */
    Optional<ClassEnrollment> findByClassSessionIdAndMemberId(Long classSessionId, Long memberId);

    Page<ClassEnrollment> findByClassSessionId(Long classSessionId, Pageable pageable);

    boolean existsByClassSessionIdAndMemberIdAndStatus(Long classSessionId, Long memberId, EnrollmentStatus status);

    /**
     * The enrolment half of the seats taken, never stored (04-Base-de-Datos §6). Grouped
     * so a whole page of sessions costs one query instead of one count per row; a single
     * session asks for a list of one.
     */
    @Query("""
           SELECT ce.classSessionId, COUNT(ce)
             FROM ClassEnrollment ce
            WHERE ce.classSessionId IN :classSessionIds
              AND ce.status <> :cancelledStatus
            GROUP BY ce.classSessionId
           """)
    List<Object[]> countActiveByClassSessionIds(Collection<Long> classSessionIds, EnrollmentStatus cancelledStatus);

    /**
     * "No permitir una cuarta inscripción en la misma semana si el límite es de 3"
     * (Enunciado). The ad-hoc JOIN ON is legal JPQL/HQL even though ClassEnrollment
     * carries no @ManyToOne to ClassSession by design (see the entity's Javadoc).
     */
    @Query("""
           SELECT COUNT(ce)
             FROM ClassEnrollment ce
             JOIN ClassSession cs ON cs.classSessionId = ce.classSessionId
            WHERE ce.memberId = :memberId
              AND ce.status <> :cancelledStatus
              AND cs.sessionDate BETWEEN :weekStart AND :weekEnd
           """)
    long countWeeklyActive(Long memberId, LocalDate weekStart, LocalDate weekEnd, EnrollmentStatus cancelledStatus);

    /**
     * A member's history for GET /members/{id}/enrollments, filtered by session date
     * and status.
     */
    @Query("""
           SELECT ce
             FROM ClassEnrollment ce
             JOIN ClassSession cs ON cs.classSessionId = ce.classSessionId
            WHERE ce.memberId = :memberId
              AND cs.sessionDate BETWEEN :from AND :to
              AND (:status IS NULL OR ce.status = :status)
            ORDER BY cs.sessionDate DESC, cs.startTime DESC
           """)
    Page<ClassEnrollment> findHistory(Long memberId, LocalDate from, LocalDate to, EnrollmentStatus status,
                                      Pageable pageable);

    /**
     * "El socio no puede [...] inscribirse a clases [...] hasta que reactive su
     * membresía": the active enrolments of a member whose session has not happened
     * yet, for MembershipService's freeze/expiry hooks.
     */
    @Query("""
           SELECT ce
             FROM ClassEnrollment ce
            WHERE ce.memberId = :memberId
              AND ce.status = :enrolledStatus
              AND ce.classSessionId IN (SELECT cs.classSessionId FROM ClassSession cs WHERE cs.sessionDate >= :today)
           """)
    List<ClassEnrollment> findActiveFutureByMember(Long memberId, EnrollmentStatus enrolledStatus, LocalDate today);

    /** Bulk cancellation when a whole session is cancelled by the administrator. */
    List<ClassEnrollment> findByClassSessionIdAndStatus(Long classSessionId, EnrollmentStatus status);
}
