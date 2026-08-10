package com.fitness.app.classes.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The member rates the class and, optionally, the trainer who taught it. One table
 * instead of two because both scores are given in the same act and share the same key:
 * which member rated which session.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ClassRating
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long    classRatingId;

    private Long    classSessionId;
    private Long    memberId;

    // SMALLINT in the DDL, same reasoning as GroupClass.durationMinutes.
    private short   classScore;
    private Short   trainerScore;

    private String  comment;
    private Instant ratedAt;
}
