package com.fitness.app.iam;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.dto.PersonContactDTO;
import com.fitness.app.iam.model.AppUser;
import com.fitness.app.iam.model.CodeStatus;
import com.fitness.app.iam.model.CodeType;
import com.fitness.app.iam.model.VerificationChannel;
import com.fitness.app.iam.model.VerificationCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Issues, delivers and consumes the temporary codes of two-factor login and
 * password recovery.
 *
 * These codes do not go through the notification module even though it owns the
 * outgoing notices: ck_notification_type has no verification type, so the data
 * model already decided they never reach the in-app inbox.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService
{
    private static final int          CODE_BOUND        = 1_000_000;   // six digits, zero padded
    private static final int          MAX_CODE_ATTEMPTS = 3;
    private static final Duration     CODE_TTL          = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM            = new SecureRandom();

    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordEncoder            passwordEncoder;
    private final JavaMailSender             mailSender;

    /**
     * Invalidates any code still outstanding for the same user and type, then
     * issues and delivers a new one. Calling it again is how a resend works.
     */
    @Transactional
    public VerificationCode issue(AppUser user, CodeType codeType, PersonContactDTO contact)
    {
        var channel     = user.getTwoFactorChannel();
        var destination = channel == VerificationChannel.SMS ? contact.phone() : contact.email();

        if (destination == null || destination.isBlank())
        {
            throw new BusinessException(ErrorCode.VERIFICATION_DESTINATION_MISSING);
        }

        expireOutstanding(user.getAppUserId(), codeType);

        var plainCode = "%06d".formatted(RANDOM.nextInt(CODE_BOUND));
        var issuedAt  = Instant.now();
        var code      = new VerificationCode();

        code.setAppUserId(user.getAppUserId());
        code.setCodeType(codeType);
        code.setChannel(channel);
        code.setCodeHash(passwordEncoder.encode(plainCode));
        code.setMaskedDestination(mask(destination, channel));
        code.setStatus(CodeStatus.ISSUED);
        code.setIssuedAt(issuedAt);
        code.setExpiresAt(issuedAt.plus(CODE_TTL));

        var saved = verificationCodeRepository.save(code);

        deliver(saved, plainCode, destination);
        log.info("CÓDIGO 2FA PARA EL USUARIO [{}]: {}", user.getUsername(), plainCode);

        return saved;
    }

    /**
     * noRollbackFor: the failed attempt has to survive the exception, otherwise
     * the counter resets on every wrong try and the limit never triggers.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public VerificationCode consume(Long verificationCodeId, String submittedCode, CodeType expectedType)
    {
        var code = verificationCodeRepository.findById(verificationCodeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VERIFICATION_CODE_NOT_FOUND));

        if (code.getCodeType() != expectedType || code.getStatus() != CodeStatus.ISSUED)
        {
            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
        }

        if (code.getExpiresAt().isBefore(Instant.now()))
        {
            code.setStatus(CodeStatus.EXPIRED);

            throw new BusinessException(ErrorCode.VERIFICATION_CODE_EXPIRED);
        }

        if (code.getAttemptCount() >= MAX_CODE_ATTEMPTS)
        {
            code.setStatus(CodeStatus.EXPIRED);

            throw new BusinessException(ErrorCode.VERIFICATION_ATTEMPTS_EXCEEDED);
        }

        if (!passwordEncoder.matches(submittedCode, code.getCodeHash()))
        {
            code.registerAttempt();

            throw new BusinessException(ErrorCode.VERIFICATION_CODE_INVALID);
        }

        code.setStatus(CodeStatus.USED);
        code.setUsedAt(Instant.now());

        return code;
    }

    private void expireOutstanding(Long appUserId, CodeType codeType)
    {
        verificationCodeRepository
                .findByAppUserIdAndCodeTypeAndStatus(appUserId, codeType, CodeStatus.ISSUED)
                .forEach(outstanding -> outstanding.setStatus(CodeStatus.EXPIRED));
    }

    /**
     * SMS has no provider, so it is written to the log; that is what schema.sql
     * already documents. A mail failure falls back to the same place, so the
     * system stays demonstrable without an SMTP server.
     */
    private void deliver(VerificationCode code, String plainCode, String destination)
    {
        if (code.getChannel() == VerificationChannel.SMS)
        {
            logCode(code, plainCode, "no SMS provider is configured");

            return;
        }

        var message = new SimpleMailMessage();

        message.setTo(destination);
        message.setSubject("Fitness App · código de verificación");
        message.setText("Tu código es %s y vence en %d minutos.".formatted(plainCode, CODE_TTL.toMinutes()));

        try
        {
            mailSender.send(message);
        }
        catch (Exception ex)
        {
            log.warn("Mail delivery failed for verificationCodeId={} destination={}: {}",
                     code.getVerificationCodeId(), code.getMaskedDestination(), ex.getMessage(), ex);

            logCode(code, plainCode, "mail delivery failed");
        }
    }

    private void logCode(VerificationCode code, String plainCode, String reason)
    {
        log.warn("VERIFICATION code delivered to the log ({}): verificationCodeId={} type={} destination={} code={}",
                 reason, code.getVerificationCodeId(), code.getCodeType(), code.getMaskedDestination(), plainCode);
    }

    /** admin@fitnessapp.local becomes a***n@fitnessapp.local; a phone keeps its last four digits. */
    private static String mask(String destination, VerificationChannel channel)
    {
        if (channel == VerificationChannel.SMS)
        {
            return destination.length() <= 4
                    ? "*".repeat(destination.length())
                    : "*".repeat(destination.length() - 4) + destination.substring(destination.length() - 4);
        }

        var atIndex = destination.indexOf('@');
        var local   = atIndex < 0 ? destination : destination.substring(0, atIndex);
        var domain  = atIndex < 0 ? ""          : destination.substring(atIndex);

        return local.length() < 3
                ? "*".repeat(local.length()) + domain
                : local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }
}
