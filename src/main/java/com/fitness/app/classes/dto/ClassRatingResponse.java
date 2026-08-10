package com.fitness.app.classes.dto;

import com.fitness.app.classes.model.ClassRating;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** A rating as the interface sees it. */
public record ClassRatingResponse(
    @JsonProperty("class_rating_id")
    Long    classRatingId,

    @JsonProperty("class_session_id")
    Long    classSessionId,

    @JsonProperty("member_id")
    Long    memberId,

    @JsonProperty("class_score")
    short   classScore,

    @JsonProperty("trainer_score")
    Short   trainerScore,

    String  comment,

    @JsonProperty("rated_at")
    Instant ratedAt
)
{
    public static ClassRatingResponse from(ClassRating rating)
    {
        return new ClassRatingResponse(
            rating.getClassRatingId(),
            rating.getClassSessionId(),
            rating.getMemberId(),
            rating.getClassScore(),
            rating.getTrainerScore(),
            rating.getComment(),
            rating.getRatedAt()
        );
    }
}
