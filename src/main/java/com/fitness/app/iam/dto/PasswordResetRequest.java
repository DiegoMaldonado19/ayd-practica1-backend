package com.fitness.app.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordResetRequest(@NotNull  Long   challengeId,
                                   @NotBlank String code,
                                   @NotBlank @Size(min = 8, max = 72) String newPassword)
{
}
