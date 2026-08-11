package com.fitness.app.training.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TrainerAlert
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long               trainerAlertId;

    private Long               memberId;
    private Long               trainerId;

    @Enumerated(EnumType.STRING)
    private TrainerAlertType   alertType;

    private String             description;

    @Enumerated(EnumType.STRING)
    private TrainerAlertStatus status;

    private Instant            createdAt;
    private Instant            resolvedAt;
    private Long               resolvedByUserId;
    private String             resolutionNotes;

    public void resolve(Instant now, Long resolvedByUserId, String resolutionNotes)
    {
        this.status            = TrainerAlertStatus.RESOLVED;
        this.resolvedAt        = now;
        this.resolvedByUserId  = resolvedByUserId;
        this.resolutionNotes   = resolutionNotes;
    }

    public void dismiss(Instant now, Long resolvedByUserId, String resolutionNotes)
    {
        this.status            = TrainerAlertStatus.DISMISSED;
        this.resolvedAt        = now;
        this.resolvedByUserId  = resolvedByUserId;
        this.resolutionNotes   = resolutionNotes;
    }
}