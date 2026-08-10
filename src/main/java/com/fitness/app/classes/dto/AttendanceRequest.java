package com.fitness.app.classes.dto;

import com.fitness.app.classes.model.EnrollmentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** "Marca la asistencia de toda la lista en una sola operación" (§3.6). */
public record AttendanceRequest(@NotEmpty @Valid List<AttendanceMark> attendances)
{
    /** status is restricted to ATTENDED/ABSENT by EnrollmentService, not by the enum itself. */
    public record AttendanceMark(
        @NotNull
        @JsonProperty("enrollment_id")
        Long             enrollmentId,

        @NotNull
        EnrollmentStatus status
    )
    {
    }
}
