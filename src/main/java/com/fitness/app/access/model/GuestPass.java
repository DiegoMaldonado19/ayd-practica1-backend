package com.fitness.app.access.model;

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

/**
 * Trial passes and guests. The visitor is registered as a person, which is what
 * makes the "one free pass per person" rule enforceable by document number.
 * Guests have no check-out, and no visit_date column: the date of the visit is
 * the date part of checked_in_at.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class GuestPass
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long           guestPassId;

    private Long           personId;

    @Enumerated(EnumType.STRING)
    private GuestPassType  passType;

    private Long           hostMemberId;
    private Instant        checkedInAt;
    private String         notes;
    private Long           registeredByUserId;
}
