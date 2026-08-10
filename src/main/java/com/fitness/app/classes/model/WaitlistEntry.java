package com.fitness.app.classes.model;

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
import java.util.Comparator;
import java.util.Map;

/**
 * Queue for a full session. There is no position column: the order is derived from the
 * member's plan tier (Elite before Premium) and then requestedAt, so it never drifts
 * when somebody leaves the queue or changes plan (04-Base-de-Datos §6).
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class WaitlistEntry
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long           waitlistEntryId;

    private Long           classSessionId;
    private Long           memberId;

    @Enumerated(EnumType.STRING)
    private WaitlistStatus status;

    private Instant        requestedAt;
    private Instant        notifiedAt;
    private Instant        confirmationDeadline;
    private Instant        resolvedAt;

    /**
     * That derived order, in one place: "dándole prioridad a los socios con Plan Élite"
     * (Enunciado) and, a igualdad de plan, quien pidió primero. Both promoting the next
     * member and showing the queue sort by this, so neither can drift from the other.
     *
     * A member with no contract in force is absent from the map and sorts last with tier
     * zero: membership never promotes them, and the queue still shows them.
     */
    public static Comparator<WaitlistEntry> byPlanPriority(Map<Long, Short> tiersByMember)
    {
        return Comparator.<WaitlistEntry, Short>comparing(entry -> tiersByMember.getOrDefault(entry.getMemberId(), (short) 0))
                .reversed()
                .thenComparing(WaitlistEntry::getRequestedAt);
    }
}
