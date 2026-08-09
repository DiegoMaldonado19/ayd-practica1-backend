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
 * Credentials and role of a person who can sign in.
 *
 * personId is a plain column and not a @ManyToOne to Person: person belongs to
 * directory, and the isolation rule of 02-Modulos §1 is that a module never maps
 * another module's entity. The contact data comes from PersonService.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class AppUser
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long                appUserId;

    private Long                personId;
    private String              username;
    private String              passwordHash;

    @Enumerated(EnumType.STRING)
    private UserRole            role;

    @Enumerated(EnumType.STRING)
    private UserStatus          status;

    private boolean             twoFactorEnabled;

    @Enumerated(EnumType.STRING)
    private VerificationChannel twoFactorChannel;

    // SMALLINT in the DDL: mapping it as int would fail ddl-auto=validate.
    private short               failedAttempts;

    private Instant             lockedUntil;
    private Instant             lastLoginAt;
    private Instant             passwordChangedAt;
    private Instant             createdAt;

    public void registerFailedAttempt()
    {
        failedAttempts = (short) (failedAttempts + 1);
    }

    public void clearFailedAttempts()
    {
        failedAttempts = 0;
        lockedUntil    = null;
    }
}
