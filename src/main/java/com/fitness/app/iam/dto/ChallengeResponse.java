package com.fitness.app.iam.dto;

import com.fitness.app.iam.model.VerificationChannel;

import java.time.Instant;

/**
 * The two-factor or recovery challenge. Answers the 202 of /auth/login and of
 * /auth/password-recoveries.
 *
 * It never carries the code: only where it was sent, masked.
 */
public record ChallengeResponse(Long                challengeId,
                                VerificationChannel channel,
                                String              maskedDestination,
                                Instant             expiresAt) implements LoginOutcome
{
}
