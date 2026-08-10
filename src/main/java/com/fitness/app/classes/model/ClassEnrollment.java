package com.fitness.app.classes.model;

import com.fitness.app.access.model.AccessChannel;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per member and session. Attendance is the status of the enrolment, not a
 * separate table (04-Base-de-Datos §6).
 *
 * classSessionId and memberId are plain Long: nothing here ever navigates back to the
 * session or the member through JPA, only through classSessionId equality in
 * ClassSessionService/EnrollmentService queries - the same reasoning as
 * MembershipFreeze.membershipId.
 *
 * channel reuses access.AccessChannel instead of a second identical enum: 04-BD §5
 * treats facility_visit.channel and class_enrollment.channel as one value type
 * (FRONT_DESK/SELF_SERVICE), not two entities.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ClassEnrollment
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long              classEnrollmentId;

    private Long              classSessionId;
    private Long              memberId;

    @Enumerated(EnumType.STRING)
    private EnrollmentStatus  status;

    @Enumerated(EnumType.STRING)
    private AccessChannel     channel;

    private Instant           enrolledAt;
    private Instant           cancelledAt;
    private Instant           attendanceMarkedAt;
    private Long              enrolledByUserId;
}
