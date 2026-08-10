package com.fitness.app.classes.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A dated occurrence of a class. Enrolment, capacity, attendance, cancellation and the
 * waitlist all hang off the session, never off the recurring definition.
 *
 * groupClass is a real @ManyToOne (unlike trainerId below) because GroupClass belongs
 * to this same module: the billboard filters by discipline, which only the class
 * carries, and the response shows the class name without a second call - exactly the
 * Membership -> MembershipPlan precedent.
 *
 * trainerId, start_time and max_capacity are copied from the class as defaults but
 * belong to the session: §3.6 lets the administrator reschedule a single date and
 * reassign its trainer. Duration is NOT copied, because rescheduling never changes it:
 * it is read from groupClass.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class ClassSession
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long          classSessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_class_id")
    private GroupClass    groupClass;

    private Long          trainerId;
    private LocalDate     sessionDate;
    private LocalTime     startTime;
    private short         maxCapacity;

    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    private String        cancellationReason;
    private Instant       openedAt;
    private Instant       closedAt;

    /** The instant the class begins, for the cancellation-window and reschedule checks. */
    public LocalDateTime startsAt()
    {
        return LocalDateTime.of(sessionDate, startTime);
    }
}
