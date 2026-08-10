package com.fitness.app.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Current caseload of each trainer.
 */
public record TrainerLoadResponse(
        @JsonProperty("trainer_id")
        Long trainerId,

        @JsonProperty("trainer_name")
        String trainerName,

        @JsonProperty("assigned_member_count")
        long assignedMemberCount,

        @JsonProperty("max_member_load")
        short maxMemberLoad,

        @JsonProperty("available_slots")
        long availableSlots)
{
}
