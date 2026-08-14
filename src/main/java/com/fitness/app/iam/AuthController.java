package com.fitness.app.iam;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.dto.ChallengeResponse;
import com.fitness.app.iam.dto.LoginOutcome;
import com.fitness.app.iam.dto.LoginRequest;
import com.fitness.app.iam.dto.PasswordRecoveryRequest;
import com.fitness.app.iam.dto.PasswordResetRequest;
import com.fitness.app.iam.dto.TokenResponse;
import com.fitness.app.iam.dto.UserResponse;
import com.fitness.app.iam.dto.VerificationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** No logic here: validate the input, call the service, translate the output. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController
{
    private final AuthService authService;

    /**
     * 202 with the challenge when two-factor is on, 200 with the token when it is
     * off. Repeating the call issues a new challenge, which is also how a resend
     * works.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginOutcome> login(@Valid @RequestBody LoginRequest request)
    {
        var outcome = authService.login(request);

        return outcome instanceof ChallengeResponse
                ? ResponseEntity.accepted().body(outcome)
                : ResponseEntity.ok(outcome);
    }

    @PostMapping("/challenges/{challengeId}/verifications")
    public TokenResponse verifyChallenge(@PathVariable Long challengeId,
                                         @Valid @RequestBody VerificationRequest request)
    {
        return authService.verifyChallenge(challengeId, request.code());
    }

    @PostMapping("/password-recoveries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ChallengeResponse startPasswordRecovery(@Valid @RequestBody PasswordRecoveryRequest request)
    {
        return authService.startPasswordRecovery(request.username());
    }

    @PostMapping("/password-resets")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request)
    {
        authService.resetPassword(request);
    }

    /**
     * ponytail: nothing to do server side. The token is stateless and there is no
     * revocation list, so the client drops it and it dies at its own expiry.
     * Add a deny list only if a session must be killable before that.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout()
    {
    }

    @GetMapping("/me")
    public UserResponse currentUser(@AuthenticationPrincipal AuthenticatedUser principal)
    {
        return authService.currentUser(principal);
    }
}
