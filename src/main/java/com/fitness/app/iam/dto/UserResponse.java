package com.fitness.app.iam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
 *
 * member_id is the exception to the one shape: only the sign-in endpoints and
 * /auth/me resolve it, and the key is absent for anyone who is not a member with
 * a file. NON_NULL goes on the component and never on the record, because the
 * other nullable fields do serialize as null and that is the house style.
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
                           Instant             lastLoginAt,

                           @JsonInclude(JsonInclude.Include.NON_NULL)
                           Long                memberId)
{
    public static UserResponse from(AppUser user, PersonContactDTO contact)
    {
        return from(user, contact, null);
    }

    /** The single mapping from entity to payload: iam has three endpoints answering this shape. */
    public static UserResponse from(AppUser user, PersonContactDTO contact, Long memberId)
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
                                user.getLastLoginAt(),
                                memberId);
    }
}
