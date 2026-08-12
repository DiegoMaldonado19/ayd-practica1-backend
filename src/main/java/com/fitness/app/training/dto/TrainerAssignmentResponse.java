package com.fitness.app.training.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fitness.app.training.model.AssignmentEndReason;
import com.fitness.app.training.model.TrainerAssignment;

import java.time.LocalDate;

public record TrainerAssignmentResponse(
    @JsonProperty("trainer_assignment_id")
    Long                trainerAssignmentId,

    @JsonProperty("member_id")
    Long                memberId,

    @JsonProperty("trainer_id")
    Long                trainerId,

    @JsonProperty("start_date")
    LocalDate           startDate,

    @JsonProperty("end_date")
    LocalDate           endDate,

    @JsonProperty("end_reason")
    AssignmentEndReason endReason
)
{
    public static TrainerAssignmentResponse from(TrainerAssignment assignment)
    {
        return new TrainerAssignmentResponse(
            assignment.getTrainerAssignmentId(),
            assignment.getMemberId(),
            assignment.getTrainerId(),
            assignment.getStartDate(),
            assignment.getEndDate(),
            assignment.getEndReason()
        );
    }
}
