package com.fitness.app.iam.dto;

import com.fitness.app.directory.dto.PersonContactDTO;
import com.fitness.app.iam.model.AppUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.iam.model.UserStatus;
import com.fitness.app.iam.model.VerificationChannel;

import java.time.Instant;

/**
 * The account as the interface sees it. Serves /auth/me, /users and /users/{id}:
 * three endpoints, one shape.
 *
 * Field names are lowerCamelCase and Jackson renders app_user_id, full_name and
 * two_factor_enabled from the global SNAKE_CASE strategy.
 */
public record UserResponse(Long                appUserId,
                           Long                personId,
                           String              username,
                           String              fullName,
                           String              email,
                           UserRole            role,
                           UserStatus          status,
                           boolean             twoFactorEnabled,
                           VerificationChannel twoFactorChannel,
                           Instant             lastLoginAt)
{
    /** The single mapping from entity to payload: iam has three endpoints answering this shape. */
    public static UserResponse from(AppUser user, PersonContactDTO contact)
    {
        return new UserResponse(user.getAppUserId(),
                                user.getPersonId(),
                                user.getUsername(),
                                contact == null ? null : contact.fullName(),
                                contact == null ? null : contact.email(),
                                user.getRole(),
                                user.getStatus(),
                                user.isTwoFactorEnabled(),
                                user.getTwoFactorChannel(),
                                user.getLastLoginAt());
    }
}
