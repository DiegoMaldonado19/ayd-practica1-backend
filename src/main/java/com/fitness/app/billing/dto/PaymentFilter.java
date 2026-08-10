package com.fitness.app.billing.dto;

import com.fitness.app.billing.model.PaymentMethod;
import com.fitness.app.billing.model.PaymentStatus;

import java.time.LocalDate;

public record PaymentFilter(Long memberId, LocalDate from, LocalDate to,
                             PaymentStatus status, PaymentMethod paymentMethod) {}