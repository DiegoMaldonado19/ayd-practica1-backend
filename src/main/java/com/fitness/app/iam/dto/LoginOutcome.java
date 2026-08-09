package com.fitness.app.iam.dto;

/**
 * A login ends in one of two shapes: the token, or the two-factor challenge.
 * Sealed so the controller can pick 200 or 202 without an instanceof on Object.
 */
public sealed interface LoginOutcome permits TokenResponse, ChallengeResponse
{
}
