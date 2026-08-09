package com.fitness.app.iam.dto;

/** The session token. Answers the 200 of /auth/login and of the challenge exchange. */
public record TokenResponse(String       accessToken,
                            String       tokenType,
                            long         expiresIn,
                            UserResponse user) implements LoginOutcome
{
    public static TokenResponse bearer(String accessToken, long expiresIn, UserResponse user)
    {
        return new TokenResponse(accessToken, "Bearer", expiresIn, user);
    }
}
