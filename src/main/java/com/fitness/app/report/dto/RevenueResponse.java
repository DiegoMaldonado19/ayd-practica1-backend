package com.fitness.app.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Revenue aggregated by period and membership plan.
 */
public record RevenueResponse(
        @JsonProperty("period")
        LocalDate period,

        @JsonProperty("plan_name")
        String planName,

        @JsonProperty("gross_amount")
        BigDecimal grossAmount,

        @JsonProperty("discount_amount")
        BigDecimal discountAmount,

        @JsonProperty("net_amount")
        BigDecimal netAmount)
{
}
