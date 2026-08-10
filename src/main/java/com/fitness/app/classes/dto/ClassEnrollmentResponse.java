package com.fitness.app.classes.dto;

import com.fitness.app.access.model.AccessChannel;
import com.fitness.app.classes.model.ClassEnrollment;
import com.fitness.app.classes.model.EnrollmentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** The enrolment as the interface sees it. */
public record ClassEnrollmentResponse(
    @JsonProperty("class_enrollment_id")
    Long              classEnrollmentId,

    @JsonProperty("class_session_id")
    Long              classSessionId,

    @JsonProperty("member_id")
    Long              memberId,

    EnrollmentStatus  status,
    AccessChannel     channel,

    @JsonProperty("enrolled_at")
    Instant           enrolledAt,

    @JsonProperty("cancelled_at")
    Instant           cancelledAt,

    @JsonProperty("attendance_marked_at")
    Instant           attendanceMarkedAt,

    @JsonProperty("enrolled_by_user_id")
    Long              enrolledByUserId
)
{
    public static ClassEnrollmentResponse from(ClassEnrollment enrollment)
    {
        return new ClassEnrollmentResponse(
            enrollment.getClassEnrollmentId(),
            enrollment.getClassSessionId(),
            enrollment.getMemberId(),
            enrollment.getStatus(),
            enrollment.getChannel(),
            enrollment.getEnrolledAt(),
            enrollment.getCancelledAt(),
            enrollment.getAttendanceMarkedAt(),
            enrollment.getEnrolledByUserId()
        );
    }
}
