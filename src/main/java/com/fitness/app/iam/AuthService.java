package com.fitness.app.iam;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.PersonService;
import com.fitness.app.directory.dto.PersonContactDTO;
import com.fitness.app.iam.dto.ChallengeResponse;
import com.fitness.app.iam.dto.LoginOutcome;
import com.fitness.app.iam.dto.LoginRequest;
import com.fitness.app.iam.dto.PasswordResetRequest;
import com.fitness.app.iam.dto.TokenResponse;
import com.fitness.app.iam.dto.UserResponse;
import com.fitness.app.iam.model.AppUser;
import com.fitness.app.iam.model.CodeType;
import com.fitness.app.iam.model.UserStatus;
import com.fitness.app.iam.model.VerificationCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Sign-in, two-factor exchange and password recovery.
 *
 * noRollbackFor at class level: a BusinessException here is an expected outcome,
 * not a failure. The failed-attempt counter and the lockout it triggers have to
 * survive the exception, otherwise the limit would never be reached.
 */
@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = BusinessException.class)
public class AuthService
{
    private static final short    MAX_LOGIN_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION      = Duration.ofMinutes(15);

    private final UserRepository           userRepository;
    private final PasswordEncoder          passwordEncoder;
    private final TokenService             tokenService;
    private final VerificationCodeService  verificationCodeService;
    private final PersonService            personService;

    public LoginOutcome login(LoginRequest request)
    {
        // Same error whether the username does not exist or the password is wrong:
        // telling them apart would leak which accounts exist.
        var user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        ensureUsable(user);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()))
        {
            registerFailedAttempt(user);

            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        user.clearFailedAttempts();

        var contact = personService.findContact(user.getPersonId());

        if (user.isTwoFactorEnabled())
        {
            return toChallenge(verificationCodeService.issue(user, CodeType.TWO_FACTOR, contact));
        }

        return openSession(user, contact);
    }

    public TokenResponse verifyChallenge(Long challengeId, String submittedCode)
    {
        var code = verificationCodeService.consume(challengeId, submittedCode, CodeType.TWO_FACTOR);
        var user = findOrFail(code.getAppUserId());

        ensureUsable(user);

        return openSession(user, personService.findContact(user.getPersonId()));
    }

    /**
     * ponytail: answers 404 when the account does not exist, which lets someone
     * enumerate usernames. The frontend needs the challenge_id to continue, and
     * a fake one would be worse. Move to a blind 202 if that ever matters.
     */
    public ChallengeResponse startPasswordRecovery(String username)
    {
        // No ensureUsable here on purpose: recovering the password of a blocked
        // account is exactly what this flow is for.
        var user    = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        var contact = personService.findContact(user.getPersonId());

        return toChallenge(verificationCodeService.issue(user, CodeType.PASSWORD_RESET, contact));
    }

    public void resetPassword(PasswordResetRequest request)
    {
        var code = verificationCodeService.consume(request.challengeId(), request.code(), CodeType.PASSWORD_RESET);
        var user = findOrFail(code.getAppUserId());

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        user.setPasswordChangedAt(Instant.now());
        user.clearFailedAttempts();

        // A successful recovery is also how a blocked or brand new account gets in.
        if (user.getStatus() != UserStatus.INACTIVE)
        {
            user.setStatus(UserStatus.ACTIVE);
        }
    }

    private AppUser findOrFail(Long appUserId)
    {
        return userRepository.findById(appUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void ensureUsable(AppUser user)
    {
        if (user.getStatus() == UserStatus.BLOCKED)
        {
            if (user.getLockedUntil() == null || user.getLockedUntil().isAfter(Instant.now()))
            {
                throw new BusinessException(ErrorCode.ACCOUNT_BLOCKED);
            }

            // The temporary lock ran out: the account unlocks on its own.
            user.setStatus(UserStatus.ACTIVE);
            user.clearFailedAttempts();
        }

        if (user.getStatus() != UserStatus.ACTIVE)
        {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }
    }

    private void registerFailedAttempt(AppUser user)
    {
        user.registerFailedAttempt();

        if (user.getFailedAttempts() >= MAX_LOGIN_ATTEMPTS)
        {
            user.setStatus(UserStatus.BLOCKED);
            user.setLockedUntil(Instant.now().plus(LOCK_DURATION));
        }
    }

    private TokenResponse openSession(AppUser user, PersonContactDTO contact)
    {
        user.setLastLoginAt(Instant.now());

        return TokenResponse.bearer(tokenService.issue(user),
                                    tokenService.expiresInSeconds(),
                                    UserResponse.from(user, contact));
    }

    private static ChallengeResponse toChallenge(VerificationCode code)
    {
        return new ChallengeResponse(code.getVerificationCodeId(),
                                     code.getChannel(),
                                     code.getMaskedDestination(),
                                     code.getExpiresAt());
    }
}
