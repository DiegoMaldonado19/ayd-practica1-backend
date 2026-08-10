package com.fitness.app.classes.dto;

import com.fitness.app.classes.model.ClassSession;
import com.fitness.app.classes.model.Discipline;
import com.fitness.app.classes.model.SessionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * The billboard row: "incluye cupos ocupados y disponibles calculados" (§3.6).
 * seatsTaken is never stored (04-Base-de-Datos §6), so the caller supplies it.
 */
public record ClassSessionResponse(
    @JsonProperty("class_session_id")
    Long           classSessionId,

    @JsonProperty("group_class_id")
    Long           groupClassId,

    @JsonProperty("group_class_name")
    String         groupClassName,

    Discipline     discipline,

    @JsonProperty("trainer_id")
    Long           trainerId,

    @JsonProperty("session_date")
    LocalDate      sessionDate,

    @JsonProperty("start_time")
    LocalTime      startTime,

    @JsonProperty("duration_minutes")
    short          durationMinutes,

    @JsonProperty("max_capacity")
    short          maxCapacity,

    @JsonProperty("seats_taken")
    int            seatsTaken,

    @JsonProperty("seats_available")
    int            seatsAvailable,

    SessionStatus  status,

    @JsonProperty("cancellation_reason")
    String         cancellationReason,

    @JsonProperty("opened_at")
    Instant        openedAt,

    @JsonProperty("closed_at")
    Instant        closedAt
)
{
    public static ClassSessionResponse from(ClassSession session, int seatsTaken)
    {
        var groupClass = session.getGroupClass();

        return new ClassSessionResponse(
            session.getClassSessionId(),
            groupClass.getGroupClassId(),
            groupClass.getName(),
            groupClass.getDiscipline(),
            session.getTrainerId(),
            session.getSessionDate(),
            session.getStartTime(),
            groupClass.getDurationMinutes(),
            session.getMaxCapacity(),
            seatsTaken,
            Math.max(0, session.getMaxCapacity() - seatsTaken),
            session.getStatus(),
            session.getCancellationReason(),
            session.getOpenedAt(),
            session.getClosedAt()
        );
    }
}
