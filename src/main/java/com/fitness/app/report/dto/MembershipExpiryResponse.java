package com.fitness.app.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Memberships expiring or recently expired.
 */
public record MembershipExpiryResponse(
        @JsonProperty("membership_id")
        Long membershipId,

        @JsonProperty("member_name")
        String memberName,

        @JsonProperty("plan_name")
        String planName,

        @JsonProperty("status")
        String status,

        @JsonProperty("end_date")
        LocalDate endDate,

        @JsonProperty("days_to_expiry")
        int daysToExpiry)
{
}
