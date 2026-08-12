package com.fitness.app.billing.dto;

import com.fitness.app.billing.model.DiscountType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PromotionRequest(
        @NotBlank @Size(max = 25) String code,
        @NotBlank @Size(max = 80) String name,
        @Size(max = 300) String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin(value = "0.01") BigDecimal discountValue,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validTo,
        @Positive Integer maxUses,
        @Positive Short maxUsesPerMember) {}