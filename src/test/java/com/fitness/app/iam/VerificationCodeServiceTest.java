package com.fitness.app.iam;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.iam.model.CodeStatus;
import com.fitness.app.iam.model.CodeType;
import com.fitness.app.iam.model.VerificationChannel;
import com.fitness.app.iam.model.VerificationCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/** The other half of the security path: a code can only be spent once, on time and within three tries. */
@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceTest
{
    private static final Long   CHALLENGE_ID = 7L;
    private static final String PLAIN_CODE   = "482913";

    @Mock private VerificationCodeRepository verificationCodeRepository;
    @Mock private PasswordEncoder            passwordEncoder;
    @Mock private JavaMailSender             mailSender;

    @InjectMocks private VerificationCodeService verificationCodeService;

    @Test
    void marksTheCodeAsUsedWhenItMatches()
    {
        var code = issuedCode();

        when(verificationCodeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(code));
        when(passwordEncoder.matches(PLAIN_CODE, code.getCodeHash())).thenReturn(true);

        var consumed = verificationCodeService.consume(CHALLENGE_ID, PLAIN_CODE, CodeType.TWO_FACTOR);

        assertEquals(CodeStatus.USED, consumed.getStatus());
        assertNotNull(consumed.getUsedAt());
    }

    @Test
    void countsTheAttemptWhenTheCodeIsWrong()
    {
        var code = issuedCode();

        when(verificationCodeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(code));
        when(passwordEncoder.matches("000000", code.getCodeHash())).thenReturn(false);

        assertEquals(ErrorCode.VERIFICATION_CODE_INVALID, expectFailure(code, "000000"));
        // The counter has to survive the exception, otherwise the limit never triggers.
        assertEquals((short) 1, code.getAttemptCount());
        assertEquals(CodeStatus.ISSUED, code.getStatus());
    }

    @Test
    void rejectsAnExpiredCode()
    {
        var code = issuedCode();

        code.setExpiresAt(Instant.now().minusSeconds(1));

        when(verificationCodeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(code));

        assertEquals(ErrorCode.VERIFICATION_CODE_EXPIRED, expectFailure(code, PLAIN_CODE));
        assertEquals(CodeStatus.EXPIRED, code.getStatus());
    }

    @Test
    void burnsTheCodeAfterThreeAttempts()
    {
        var code = issuedCode();

        code.registerAttempt();
        code.registerAttempt();
        code.registerAttempt();

        when(verificationCodeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(code));

        assertEquals(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED, expectFailure(code, PLAIN_CODE));
        assertEquals(CodeStatus.EXPIRED, code.getStatus());
    }

    @Test
    void refusesToSpendATwoFactorCodeOnAPasswordReset()
    {
        when(verificationCodeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(issuedCode()));

        assertEquals(ErrorCode.VERIFICATION_CODE_INVALID,
                     assertThrows(BusinessException.class,
                                  () -> verificationCodeService.consume(CHALLENGE_ID, PLAIN_CODE,
                                                                        CodeType.PASSWORD_RESET)).getErrorCode());
    }

    private ErrorCode expectFailure(VerificationCode code, String submittedCode)
    {
        return assertThrows(BusinessException.class,
                            () -> verificationCodeService.consume(CHALLENGE_ID, submittedCode, code.getCodeType()))
                .getErrorCode();
    }

    private static VerificationCode issuedCode()
    {
        var code = new VerificationCode();

        code.setVerificationCodeId(CHALLENGE_ID);
        code.setAppUserId(1L);
        code.setCodeType(CodeType.TWO_FACTOR);
        code.setChannel(VerificationChannel.EMAIL);
        code.setCodeHash("$2a$10$hashed-code");
        code.setMaskedDestination("a***n@fitnessapp.local");
        code.setStatus(CodeStatus.ISSUED);
        code.setIssuedAt(Instant.now());
        code.setExpiresAt(Instant.now().plusSeconds(600));

        return code;
    }
}
