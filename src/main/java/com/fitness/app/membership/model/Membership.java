package com.fitness.app.membership.model;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One contract. The renewal history is the set of rows of one member ordered by
 * start_date, so there is no "previous contract" link.
 *
 * paid_price freezes what the member agreed to, so the administrator can change
 * MembershipPlan.price without rewriting contracts already signed.
 *
 * plan is a real @ManyToOne because MembershipPlan belongs to this same module and
 * the detail serves the benefits; memberId and createdByUserId are plain Long
 * because they cross into directory and iam, and the isolation rule of 02-Modulos
 * §1 forbids mapping another module's entity.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Membership
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long               membershipId;

    private Long               memberId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_plan_id")
    private MembershipPlan     plan;

    private BigDecimal         paidPrice;
    private LocalDate          startDate;
    private LocalDate          endDate;

    @Enumerated(EnumType.STRING)
    private MembershipStatus   status;

    private LocalDate          cancelledOn;

    @Enumerated(EnumType.STRING)
    private CancellationReason cancellationReason;

    private String             notes;
    private Long               createdByUserId;
    private Instant            createdAt;

    /** Never negative: an expired contract has zero days left, not days owed. */
    public long daysRemaining(LocalDate today)
    {
        return Math.max(0, ChronoUnit.DAYS.between(today, endDate));
    }
}
