package com.fitness.app.training.dto;

import com.fitness.app.training.model.TrainerAlert;
import com.fitness.app.training.model.TrainerAlertStatus;
import com.fitness.app.training.model.TrainerAlertType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** One escalation as the queue sees it. */
public record TrainerAlertResponse(
    @JsonProperty("trainer_alert_id")
    Long               trainerAlertId,

    @JsonProperty("member_id")
    Long               memberId,

    @JsonProperty("trainer_id")
    Long               trainerId,

    @JsonProperty("alert_type")
    TrainerAlertType   alertType,

    String             description,

    TrainerAlertStatus status,

    @JsonProperty("created_at")
    Instant            createdAt,

    @JsonProperty("resolved_at")
    Instant            resolvedAt,

    @JsonProperty("resolved_by_user_id")
    Long               resolvedByUserId,

    @JsonProperty("resolution_notes")
    String             resolutionNotes
)
{
    public static TrainerAlertResponse from(TrainerAlert alert)
    {
        return new TrainerAlertResponse(alert.getTrainerAlertId(),
                                        alert.getMemberId(),
                                        alert.getTrainerId(),
                                        alert.getAlertType(),
                                        alert.getDescription(),
                                        alert.getStatus(),
                                        alert.getCreatedAt(),
                                        alert.getResolvedAt(),
                                        alert.getResolvedByUserId(),
                                        alert.getResolutionNotes());
    }
}