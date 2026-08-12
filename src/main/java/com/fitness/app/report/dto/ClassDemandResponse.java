package com.fitness.app.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Demand metrics for each group class.
 */
public record ClassDemandResponse(
        @JsonProperty("class_id")
        Long classId,

        @JsonProperty("class_name")
        String className,

        @JsonProperty("total_sessions")
        long totalSessions,

        @JsonProperty("total_enrollments")
        long totalEnrollments,

        @JsonProperty("average_occupancy_rate")
        Double averageOccupancyRate,

        @JsonProperty("waitlist_activations_count")
        long waitlistActivationsCount)
{
}
