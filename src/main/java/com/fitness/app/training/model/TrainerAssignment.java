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
import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TrainerAssignment
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long                trainerAssignmentId;

    private Long                memberId;
    private Long                trainerId;
    private LocalDate           startDate;
    private LocalDate           endDate;

    @Enumerated(EnumType.STRING)
    private AssignmentEndReason endReason;

    private Long                assignedByUserId;
    private Instant             createdAt;

    public void close(LocalDate endDate, AssignmentEndReason endReason)
    {
        this.endDate    = endDate;
        this.endReason  = endReason;
    }
}
