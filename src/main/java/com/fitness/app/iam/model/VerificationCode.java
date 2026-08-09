package com.fitness.app.iam.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A temporary code for two-factor login or password recovery. The code itself is
 * never stored: only its BCrypt hash, the same way a password is.
 *
 * maskedDestination is a snapshot of where it was sent, so a later change of
 * email does not rewrite the audit trail.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class VerificationCode
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long                verificationCodeId;

    private Long                appUserId;

    @Enumerated(EnumType.STRING)
    private CodeType            codeType;

    @Enumerated(EnumType.STRING)
    private VerificationChannel channel;

    private String              codeHash;
    private String              maskedDestination;

    @Enumerated(EnumType.STRING)
    private CodeStatus          status;

    // SMALLINT in the DDL, same reason as AppUser.failedAttempts.
    private short               attemptCount;

    private Instant             issuedAt;
    private Instant             expiresAt;
    private Instant             usedAt;

    public void registerAttempt()
    {
        attemptCount = (short) (attemptCount + 1);
    }
}
