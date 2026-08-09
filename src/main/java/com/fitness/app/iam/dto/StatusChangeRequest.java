package com.fitness.app.iam.dto;

import com.fitness.app.iam.model.UserStatus;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotNull UserStatus status)
{
}
