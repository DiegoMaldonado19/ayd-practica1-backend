package com.fitness.app.billing.dto;

import com.fitness.app.billing.model.Payment;
import com.fitness.app.billing.model.PaymentConcept;
import com.fitness.app.billing.model.PaymentMethod;
import com.fitness.app.billing.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long paymentId, Long memberId, Long membershipId, Long guestPassId,
        PaymentConcept concept, PaymentMethod paymentMethod,
        BigDecimal grossAmount, Long promotionId, BigDecimal discountAmount,
        BigDecimal netAmount, PaymentStatus status, Instant paidAt,
        String receiptSeries, Integer receiptNumber, Instant receiptIssuedAt,
        Instant voidedAt, String voidReason, Long registeredByUserId) {

    public static PaymentResponse from(Payment payment) {
        // Neto SIEMPRE derivado, nunca almacenado (04-Base-de-Datos.md §6).
        BigDecimal net = payment.getGrossAmount().subtract(payment.getDiscountAmount());
        return new PaymentResponse(
                payment.getPaymentId(), payment.getMemberId(), payment.getMembershipId(),
                payment.getGuestPassId(), payment.getConcept(), payment.getPaymentMethod(),
                payment.getGrossAmount(),
                payment.getPromotion() != null ? payment.getPromotion().getPromotionId() : null,
                payment.getDiscountAmount(), net, payment.getStatus(), payment.getPaidAt(),
                payment.getReceiptSeries(), payment.getReceiptNumber(), payment.getReceiptIssuedAt(),
                payment.getVoidedAt(), payment.getVoidReason(), payment.getRegisteredByUserId());
    }
}