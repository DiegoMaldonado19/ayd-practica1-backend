package com.fitness.app.classes.dto;

import com.fitness.app.classes.model.WaitlistEntry;
import com.fitness.app.classes.model.WaitlistStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** A queue entry, already in priority order when it comes from the session listing. */
public record WaitlistEntryResponse(
    @JsonProperty("waitlist_entry_id")
    Long           waitlistEntryId,

    @JsonProperty("class_session_id")
    Long           classSessionId,

    @JsonProperty("member_id")
    Long           memberId,

    WaitlistStatus status,

    @JsonProperty("requested_at")
    Instant        requestedAt,

    @JsonProperty("notified_at")
    Instant        notifiedAt,

    @JsonProperty("confirmation_deadline")
    Instant        confirmationDeadline,

    @JsonProperty("resolved_at")
    Instant        resolvedAt
)
{
    public static WaitlistEntryResponse from(WaitlistEntry entry)
    {
        return new WaitlistEntryResponse(
            entry.getWaitlistEntryId(),
            entry.getClassSessionId(),
            entry.getMemberId(),
            entry.getStatus(),
            entry.getRequestedAt(),
            entry.getNotifiedAt(),
            entry.getConfirmationDeadline(),
            entry.getResolvedAt()
        );
    }
}
