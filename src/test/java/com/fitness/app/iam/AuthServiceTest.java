package com.fitness.app.iam;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.directory.PersonService;
import com.fitness.app.directory.dto.PersonContactDTO;
import com.fitness.app.iam.dto.ChallengeResponse;
import com.fitness.app.iam.dto.LoginRequest;
import com.fitness.app.iam.dto.TokenResponse;
import com.fitness.app.iam.model.AppUser;
import com.fitness.app.iam.model.CodeStatus;
import com.fitness.app.iam.model.CodeType;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.iam.model.UserStatus;
import com.fitness.app.iam.model.VerificationChannel;
import com.fitness.app.iam.model.VerificationCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The lockout is the one rule the Postman collection cannot exercise: proving it
 * means failing five sign-ins in a row, which leaves the seeded administrator
 * unusable for the next run.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest
{
    private static final Long   PERSON_ID = 9L;
    private static final String USERNAME  = "admin";

    @Mock  private UserRepository          userRepository;
    @Mock  private PasswordEncoder         passwordEncoder;
    @Mock  private TokenService            tokenService;
    @Mock  private VerificationCodeService verificationCodeService;
    @Mock  private PersonService           personService;
    @Mock  private MemberService           memberService;

    @InjectMocks private AuthService authService;

    @Test
    void blocksTheAccountAfterFiveFailedAttempts()
    {
        var user    = activeUser(false);
        var request = new LoginRequest(USERNAME, "wrong");

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        for (var attempt = 1; attempt <= 5; attempt++)
        {
            assertEquals(ErrorCode.INVALID_CREDENTIALS,
                         assertThrows(BusinessException.class, () -> authService.login(request)).getErrorCode());
        }

        assertEquals(UserStatus.BLOCKED, user.getStatus());
        assertEquals((short) 5, user.getFailedAttempts());
        assertNotNull(user.getLockedUntil());

        // From here on the answer changes: the account is blocked, not the password wrong.
        assertEquals(ErrorCode.ACCOUNT_BLOCKED,
                     assertThrows(BusinessException.class, () -> authService.login(request)).getErrorCode());
    }

    @Test
    void unlocksOnItsOwnOnceTheTemporaryLockRanOut()
    {
        var user = activeUser(false);

        user.setStatus(UserStatus.BLOCKED);
        user.registerFailedAttempt();
        user.setLockedUntil(Instant.now().minusSeconds(60));

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(personService.findContact(PERSON_ID)).thenReturn(contact());
        when(tokenService.issue(user)).thenReturn("signed-jwt");
        when(tokenService.expiresInSeconds()).thenReturn(3600L);

        var outcome = authService.login(new LoginRequest(USERNAME, "Admin123*"));

        assertInstanceOf(TokenResponse.class, outcome);
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals((short) 0, user.getFailedAttempts());
    }

    @Test
    void returnsTheTokenWhenTwoFactorIsOff()
    {
        var user = activeUser(false);

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(personService.findContact(PERSON_ID)).thenReturn(contact());
        when(tokenService.issue(user)).thenReturn("signed-jwt");
        when(tokenService.expiresInSeconds()).thenReturn(3600L);

        var outcome = authService.login(new LoginRequest(USERNAME, "Admin123*"));

        assertInstanceOf(TokenResponse.class, outcome);
        assertEquals("signed-jwt", ((TokenResponse) outcome).accessToken());
        assertNotNull(user.getLastLoginAt());
        // An administrator has no member file, so the payload carries no member_id.
        assertNull(((TokenResponse) outcome).user().memberId());
    }

    @Test
    void stampsTheMemberIdWhenTheAccountIsAMember()
    {
        var user = activeUser(false);

        user.setRole(UserRole.MEMBER);

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(personService.findContact(PERSON_ID)).thenReturn(contact());
        when(tokenService.issue(user)).thenReturn("signed-jwt");
        when(tokenService.expiresInSeconds()).thenReturn(3600L);
        when(memberService.findMemberIdByPersonId(PERSON_ID)).thenReturn(Optional.of(42L));

        var outcome = authService.login(new LoginRequest(USERNAME, "Socio123*"));

        assertEquals(42L, ((TokenResponse) outcome).user().memberId());
    }

    @Test
    void returnsTheChallengeWhenTwoFactorIsOn()
    {
        var user = activeUser(true);

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(personService.findContact(PERSON_ID)).thenReturn(contact());
        when(verificationCodeService.issue(user, CodeType.TWO_FACTOR, contact())).thenReturn(issuedCode());

        var outcome = authService.login(new LoginRequest(USERNAME, "Admin123*"));

        assertInstanceOf(ChallengeResponse.class, outcome);
        assertEquals("a***n@fitnessapp.local", ((ChallengeResponse) outcome).maskedDestination());
        // No session is opened until the code is exchanged.
        assertEquals(null, user.getLastLoginAt());
    }

    @Test
    void rejectsAnInactiveAccountBeforeCheckingThePassword()
    {
        var user = activeUser(false);

        user.setStatus(UserStatus.PENDING_ACTIVATION);

        when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));

        assertEquals(ErrorCode.ACCOUNT_INACTIVE,
                     assertThrows(BusinessException.class,
                                  () -> authService.login(new LoginRequest(USERNAME, "Admin123*"))).getErrorCode());
    }

    private static AppUser activeUser(boolean twoFactorEnabled)
    {
        var user = new AppUser();

        user.setAppUserId(1L);
        user.setPersonId(PERSON_ID);
        user.setUsername(USERNAME);
        user.setPasswordHash("$2a$10$stored-hash");
        user.setRole(UserRole.ADMIN);
        user.setStatus(UserStatus.ACTIVE);
        user.setTwoFactorEnabled(twoFactorEnabled);
        user.setTwoFactorChannel(VerificationChannel.EMAIL);

        return user;
    }

    private static PersonContactDTO contact()
    {
        return new PersonContactDTO(PERSON_ID, "System Administrator", "admin@fitnessapp.local", "00000000");
    }

    private static VerificationCode issuedCode()
    {
        var code = new VerificationCode();

        code.setVerificationCodeId(7L);
        code.setAppUserId(1L);
        code.setCodeType(CodeType.TWO_FACTOR);
        code.setChannel(VerificationChannel.EMAIL);
        code.setStatus(CodeStatus.ISSUED);
        code.setMaskedDestination("a***n@fitnessapp.local");
        code.setIssuedAt(Instant.now());
        code.setExpiresAt(Instant.now().plusSeconds(600));

        return code;
    }
}
