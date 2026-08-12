// ReceiptResponse.java
package com.fitness.app.billing.dto;

import com.fitness.app.billing.model.Payment;
import com.fitness.app.billing.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

public record ReceiptResponse(
        Long paymentId, String receiptSeries, Integer receiptNumber,
        Instant receiptIssuedAt, BigDecimal grossAmount, BigDecimal discountAmount,
        BigDecimal netAmount, PaymentMethod paymentMethod, Instant paidAt) {

    public static ReceiptResponse from(Payment payment) {
        BigDecimal net = payment.getGrossAmount().subtract(payment.getDiscountAmount());
        return new ReceiptResponse(
            payment.getPaymentId(), payment.getReceiptSeries(), payment.getReceiptNumber(),
            payment.getReceiptIssuedAt(), payment.getGrossAmount(), payment.getDiscountAmount(),
            net, payment.getPaymentMethod(), payment.getPaidAt());
    }
}