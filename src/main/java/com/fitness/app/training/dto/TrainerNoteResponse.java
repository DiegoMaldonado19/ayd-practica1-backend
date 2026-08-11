package com.fitness.app.training.dto;

import com.fitness.app.training.model.TrainerNote;
import com.fitness.app.training.model.TrainerNoteType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDate;

/** One follow-up note as the interface sees it. */
public record TrainerNoteResponse(
    @JsonProperty("trainer_note_id")
    Long            trainerNoteId,

    @JsonProperty("member_id")
    Long            memberId,

    @JsonProperty("trainer_id")
    Long            trainerId,

    @JsonProperty("note_type")
    TrainerNoteType noteType,

    String          content,

    @JsonProperty("reference_date")
    LocalDate       referenceDate,

    @JsonProperty("created_at")
    Instant         createdAt
)
{
    public static TrainerNoteResponse from(TrainerNote note)
    {
        return new TrainerNoteResponse(note.getTrainerNoteId(),
                                       note.getMemberId(),
                                       note.getTrainerId(),
                                       note.getNoteType(),
                                       note.getContent(),
                                       note.getReferenceDate(),
                                       note.getCreatedAt());
    }
}