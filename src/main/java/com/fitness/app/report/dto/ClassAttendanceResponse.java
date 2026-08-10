package com.fitness.app.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Attendance metrics for a class session.
 */
public record ClassAttendanceResponse(
        @JsonProperty("session_id")
        Long sessionId,

        @JsonProperty("class_name")
        String className,

        @JsonProperty("trainer_name")
        String trainerName,

        @JsonProperty("session_date")
        LocalDate sessionDate,

        @JsonProperty("session_time")
        LocalTime sessionTime,

        @JsonProperty("attended_count")
        long attendedCount,

        @JsonProperty("absent_count")
        long absentCount,

        @JsonProperty("cancelled_count")
        long cancelledCount,

        @JsonProperty("attendance_rate")
        Double attendanceRate)
{
}
