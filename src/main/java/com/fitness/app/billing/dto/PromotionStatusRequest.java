package com.fitness.app.billing.dto;

import jakarta.validation.constraints.NotNull;

public record PromotionStatusRequest(@NotNull Boolean active) {}