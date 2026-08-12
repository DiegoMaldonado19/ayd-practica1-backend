package com.fitness.app.billing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PaymentVoidRequest(@NotBlank @Size(max = 200) String reason) {}