package com.fitness.app.membership.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * A pause period. The real start and reactivation dates are what allow the expiry
 * date to be recomputed, so the number of frozen days is derived from them and never
 * stored. There is no status column either: the freeze is in progress while
 * reactivatedOn is null.
 *
 * membershipId is a plain Long and not a @ManyToOne because nothing ever navigates
 * from a freeze back to its contract.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MembershipFreeze
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long         membershipFreezeId;

    private Long         membershipId;

    @Enumerated(EnumType.STRING)
    private FreezeReason reason;

    private String       reasonDetail;
    private LocalDate    requestedOn;
    private LocalDate    startDate;
    private LocalDate    expectedEndDate;
    private LocalDate    reactivatedOn;
    private Long         authorizedByUserId;
    private Long         reactivatedByUserId;

    /**
     * Days the contract stayed paused. A freeze still in progress counts up to the
     * given date, which is what lets the cycle be checked before reactivating.
     */
    public long frozenDays(LocalDate today)
    {
        var until = reactivatedOn == null ? today : reactivatedOn;

        return Math.max(0, ChronoUnit.DAYS.between(startDate, until));
    }
}
