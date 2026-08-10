package com.fitness.app.billing.dto;

import com.fitness.app.billing.model.DiscountType;
import com.fitness.app.billing.model.Promotion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PromotionResponse(
        Long promotionId, String code, String name, String description,
        DiscountType discountType, BigDecimal discountValue,
        LocalDate validFrom, LocalDate validTo,
        Integer maxUses, Short maxUsesPerMember,
        Long authorizedByUserId, Instant authorizedAt, Boolean active) {

    public static PromotionResponse from(Promotion p) {
        return new PromotionResponse(
                p.getPromotionId(), p.getCode(), p.getName(), p.getDescription(),
                p.getDiscountType(), p.getDiscountValue(),
                p.getValidFrom(), p.getValidTo(),
                p.getMaxUses(), p.getMaxUsesPerMember(),
                p.getAuthorizedByUserId(), p.getAuthorizedAt(), p.getActive());
    }
}