package com.fitness.app.iam.dto;

import com.fitness.app.iam.model.VerificationChannel;
import jakarta.validation.constraints.NotNull;

/** A null channel keeps the one the account already had. */
public record TwoFactorRequest(@NotNull Boolean             enabled,
                                       VerificationChannel channel)
{
}
