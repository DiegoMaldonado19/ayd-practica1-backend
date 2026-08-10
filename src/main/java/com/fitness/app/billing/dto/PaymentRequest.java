package com.fitness.app.billing.dto;

import com.fitness.app.billing.model.PaymentConcept;
import com.fitness.app.billing.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * amount solo se usa cuando concept = OTHER  es decir que el monto declarado en mostrador.
 * Cuando concept = MEMBERSHIP, el gross_amount se deriva SIEMPRE de
 * membership.paidPrice en el backend cualquier valor enviado aquí se ignora
 */
public record PaymentRequest(
        Long memberId,
        Long membershipId,
        Long guestPassId,
        @NotNull PaymentConcept concept,
        @NotNull PaymentMethod paymentMethod,
        Long promotionId,
        @DecimalMin(value = "0.01", message = "amount debe ser mayor a 0") BigDecimal amount) {}