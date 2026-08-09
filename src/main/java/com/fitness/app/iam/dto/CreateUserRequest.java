package com.fitness.app.iam.dto;

import com.fitness.app.iam.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Credentials for a person who already has a file. personId is required and
 * checked against directory: the account of someone the gym holds no record of is
 * exactly what this endpoint must not create.
 *
 * The 72 byte cap on the password is BCrypt's, not a policy.
 */
public record CreateUserRequest(@NotNull                           Long     personId,
                                @NotBlank @Size(max = 50)          String   username,
                                @NotBlank @Size(min = 8, max = 72) String   password,
                                @NotNull                           UserRole role)
{
}
