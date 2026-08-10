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

/**
 * One stretch of the member-trainer relationship. A reassignment closes the open row
 * and opens another, which is how the statement's "transferir la cartera sin perder el
 * historial" is satisfied: nothing is overwritten.
 *
 * memberId, trainerId and assignedByUserId are plain Longs: Member and Trainer belong
 * to directory and AppUser to iam, and the isolation rule forbids navigating there
 * through JPA (02-Modulos §1).
 *
 * endDate and endReason are inseparable (ck_assign_closed): a row is open or closed,
 * never half of each.
 */
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

    /** Closes the stretch. Both columns move together or ck_assign_closed rejects the row. */
    public void close(LocalDate endDate, AssignmentEndReason endReason)
    {
        this.endDate    = endDate;
        this.endReason  = endReason;
    }
}
