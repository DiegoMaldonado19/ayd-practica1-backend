package com.fitness.app.access.dto;

import com.fitness.app.access.model.AccessChannel;
import com.fitness.app.access.model.FacilityVisit;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Visit as the interface sees it.
 */
public record FacilityVisitResponse(
    @JsonProperty("facility_visit_id")
    Long           facilityVisitId,

    @JsonProperty("member_id")
    Long           memberId,

    @JsonProperty("membership_id")
    Long           membershipId,

    @JsonProperty("checked_in_at")
    Instant        checkedInAt,

    @JsonProperty("checked_out_at")
    Instant        checkedOutAt,

    @JsonProperty("channel")
    AccessChannel  channel,

    @JsonProperty("minutes_inside")
    long           minutesInside
)
{
    public static FacilityVisitResponse from(FacilityVisit visit)
    {
        return new FacilityVisitResponse(
            visit.getFacilityVisitId(),
            visit.getMemberId(),
            visit.getMembershipId(),
            visit.getCheckedInAt(),
            visit.getCheckedOutAt(),
            visit.getChannel(),
            visit.minutesInside(Instant.now())
        );
    }
}
