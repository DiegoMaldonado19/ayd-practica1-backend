package com.fitness.app.iam.dto;

import com.fitness.app.iam.model.UserRole;

/**
 * The authenticated principal, built from the JWT claims. Controllers receive it
 * with @AuthenticationPrincipal, which is why no helper reads the security
 * context by hand.
 */
public record AuthenticatedUser(Long     appUserId,
                                String   username,
                                UserRole role)
{
}
