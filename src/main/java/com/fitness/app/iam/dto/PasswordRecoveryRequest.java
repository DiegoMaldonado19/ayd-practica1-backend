package com.fitness.app.iam.dto;

import jakarta.validation.constraints.NotBlank;

public record PasswordRecoveryRequest(@NotBlank String username)
{
}
