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
import java.time.temporal.ChronoUnit;

/**
 * Check-in / check-out. membership_id records under which contract the member
 * walked in, which keeps the attendance report correct after a renewal.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class FacilityVisit
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long           facilityVisitId;

    private Long           memberId;
    private Long           membershipId;
    private Instant        checkedInAt;
    private Instant        checkedOutAt;

    @Enumerated(EnumType.STRING)
    private AccessChannel  channel;

    private Long           checkedInByUserId;
    private Long           checkedOutByUserId;

    /** Time spent inside, or zero if still open. */
    public long minutesInside(Instant now)
    {
        var until = checkedOutAt != null ? checkedOutAt : now;
        return ChronoUnit.MINUTES.between(checkedInAt, until);
    }
}
