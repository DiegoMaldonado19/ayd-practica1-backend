package com.fitness.app.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Usage statistics for guest passes and trial days.
 */
public record GuestPassUsageResponse(
        @JsonProperty("guest_pass_id")
        Long guestPassId,

        @JsonProperty("guest_name")
        String guestName,

        @JsonProperty("pass_type")
        String passType,

        @JsonProperty("visit_date")
        LocalDate visitDate,

        @JsonProperty("converted_to_member")
        boolean convertedToMember,

        @JsonProperty("amount_paid")
        BigDecimal amountPaid)
{
}
