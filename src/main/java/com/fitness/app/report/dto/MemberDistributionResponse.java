package com.fitness.app.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Distribution of members by plan and membership status.
 */
public record MemberDistributionResponse(
        @JsonProperty("plan_name")
        String planName,

        @JsonProperty("status")
        String status,

        @JsonProperty("member_count")
        long memberCount)
{
}
