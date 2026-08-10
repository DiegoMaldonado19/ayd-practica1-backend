package com.fitness.app.notification;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.dto.UserResponse;
import com.fitness.app.notification.dto.NotificationResponse;
import com.fitness.app.notification.dto.NotificationStatusRequest;
import com.fitness.app.notification.model.Notification;
import com.fitness.app.notification.model.NotificationChannel;
import com.fitness.app.notification.model.NotificationStatus;
import com.fitness.app.notification.model.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;

/**
 * Composes and delivers the notices of the system, and serves the in-app inbox
 * (02-Modulos §2.9).
 *
 * The bodies are Java text blocks and not rows of a template table: there are ten
 * fixed notices, and as data the compiler would stop checking their parameters
 * (04-Base-de-Datos §5).
 *
 * Every module that notifies calls this service; this service calls only directory,
 * which is the single direction the dependency matrix of 02-Modulos §3 allows. That
 * is why the public methods take member ids and plain text instead of the entities
 * of membership or classes.
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class NotificationService
{
    private static final int FAILURE_REASON_MAX = 300;

    private final NotificationRepository notificationRepository;
    private final MemberService          memberService;
    private final JavaMailSender         mailSender;

    // -- Inbox (§3.9) -------------------------------------------------------------

    /** "Bandeja del usuario autenticado": the account is the principal, never a parameter. */
    @Transactional(readOnly = true)
    public Page<NotificationResponse> inbox(NotificationStatus status, AuthenticatedUser principal, Pageable pageable)
    {
        return notificationRepository.findInbox(principal.appUserId(), status, pageable)
                .map(NotificationResponse::from);
    }

    /**
     * "Marca una notificación como leída" (§3.9). READ is the only destination the
     * API accepts: the other three are delivery states that only this service writes.
     */
    public NotificationResponse changeStatus(Long notificationId, NotificationStatusRequest request,
                                             AuthenticatedUser principal)
    {
        var notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getAppUserId().equals(principal.appUserId()))
        {
            throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
        }

        if (request.status() != NotificationStatus.READ)
        {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        // Reading twice is not an error; the first timestamp is the one that counts.
        if (notification.getReadAt() == null)
        {
            notification.setStatus(NotificationStatus.READ);
            notification.setReadAt(Instant.now());
        }

        return NotificationResponse.from(notification);
    }

    // -- Notices the other modules emit -------------------------------------------

    /**
     * "El sistema debe notificar al socio con un margen de días antes del vencimiento
     * (por ejemplo, 5 días antes)" (Enunciado). Emitted by membership's 07:00 task.
     */
    public void membershipExpiring(Long memberId, LocalDate endDate, int daysLeft)
    {
        notifyMember(memberId, NotificationType.MEMBERSHIP_EXPIRING, NotificationChannel.EMAIL,
                     "Tu membresía vence pronto",
                     """
                     Tu membresía vence el %s, dentro de %d día(s).
                     Renuévala en recepción para no perder el acceso a las instalaciones y a tus clases.
                     """.formatted(endDate, daysLeft));
    }

    /**
     * "Volver a notificar el día en que la membresía pasa a estado vencida"
     * (Enunciado). Emitted by membership's 00:05 task.
     */
    public void membershipExpired(Long memberId, LocalDate endDate)
    {
        notifyMember(memberId, NotificationType.MEMBERSHIP_EXPIRED, NotificationChannel.EMAIL,
                     "Tu membresía venció",
                     """
                     Tu membresía venció el %s y tus inscripciones futuras fueron canceladas.
                     Renuévala en recepción para recuperar el acceso.
                     """.formatted(endDate));
    }

    /**
     * expectedEndDate is nullable: the statement makes the estimated reactivation date
     * optional, so the period is worded without it instead of printing "al null".
     */
    public void membershipFrozen(Long memberId, LocalDate startDate, LocalDate expectedEndDate)
    {
        var period = expectedEndDate == null
                             ? "a partir del %s".formatted(startDate)
                             : "del %s al %s".formatted(startDate, expectedEndDate);

        notifyMember(memberId, NotificationType.MEMBERSHIP_FROZEN, NotificationChannel.IN_APP,
                     "Tu membresía quedó congelada",
                     """
                     Congelamos tu membresía %s.
                     Los días congelados se sumarán a tu vencimiento cuando la reactives.
                     """.formatted(period));
    }

    public void membershipReactivated(Long memberId, LocalDate endDate)
    {
        notifyMember(memberId, NotificationType.MEMBERSHIP_REACTIVATED, NotificationChannel.IN_APP,
                     "Tu membresía está activa de nuevo",
                     """
                     Reactivamos tu membresía. Su nueva fecha de vencimiento es el %s,
                     ya con los días congelados sumados.
                     """.formatted(endDate));
    }

    /**
     * "El sistema debe liberar el cupo y notificar automáticamente al primer socio en
     * la lista de espera" (Enunciado). classes decides who is first -Élite before
     * Premium- and this only delivers the notice.
     */
    public void waitlistSeatAvailable(Long memberId, String className, LocalDate sessionDate, Instant deadline)
    {
        notifyMember(memberId, NotificationType.WAITLIST_SEAT_AVAILABLE, NotificationChannel.EMAIL,
                     "Se liberó un cupo en tu clase",
                     """
                     Se liberó un cupo en %s del %s y eres el siguiente en la lista de espera.
                     Confirma antes de %s o el cupo pasará al siguiente socio.
                     """.formatted(className, sessionDate, deadline));
    }

    /** "Cancela la sesión con motivo y notifica a los inscritos" (§3.6). */
    public void sessionCancelled(Collection<Long> memberIds, String className, LocalDate sessionDate, String reason)
    {
        memberIds.forEach(memberId ->
                notifyMember(memberId, NotificationType.SESSION_CANCELLED, NotificationChannel.EMAIL,
                             "Se canceló tu clase",
                             """
                             Cancelamos %s del %s.
                             Motivo: %s
                             Tu inscripción fue cancelada y no consume tu límite semanal.
                             """.formatted(className, sessionDate, reason)));
    }

    /** "Reprograma: fecha, hora, cupo o entrenador de esa sesión. Notifica a los inscritos" (§3.6). */
    public void sessionRescheduled(Collection<Long> memberIds, String className, LocalDate sessionDate,
                                   LocalTime startTime)
    {
        memberIds.forEach(memberId ->
                notifyMember(memberId, NotificationType.SESSION_RESCHEDULED, NotificationChannel.IN_APP,
                             "Se reprogramó tu clase",
                             """
                             %s cambió de horario. Su nueva fecha es el %s a las %s.
                             Tu inscripción se mantiene.
                             """.formatted(className, sessionDate, startTime)));
    }

    /** "La baja de un entrenador dispara el aviso de transferencia de cartera" (§3.2). */
    public void trainerAssigned(Long memberId, String trainerName)
    {
        notifyMember(memberId, NotificationType.TRAINER_ASSIGNED, NotificationChannel.IN_APP,
                     "Tienes un nuevo entrenador",
                     """
                     Tu nuevo entrenador asignado es %s.
                     Tus rutinas y tu historial de progreso se conservan.
                     """.formatted(trainerName));
    }

    // -- Delivery -----------------------------------------------------------------

    /**
     * Resolves the account behind the member and delivers. A member with no account
     * has nowhere to be notified: the notice is skipped and logged, never thrown, or
     * one member without credentials would abort the whole scheduled sweep.
     */
    private void notifyMember(Long memberId, NotificationType type, NotificationChannel channel,
                              String title, String message)
    {
        memberService.accountOf(memberId).ifPresentOrElse(
                account -> send(account, type, channel, title, message),
                () -> log.debug("Notice {} skipped: memberId={} has no account", type, memberId));
    }

    /**
     * The row is the inbox entry and the delivery record at once, so every notice is
     * stored whatever the channel does. IN_APP is delivered by existing; EMAIL is
     * attempted and falls back to the log, the same contract VerificationCodeService
     * already uses so the system stays demonstrable without an SMTP server.
     *
     * ponytail: a FAILED row is never retried. ix_notification_pending is the partial
     * index a retry sweep would use, but 02-Modulos §2.9 fixes the system at two
     * scheduled tasks, so it is not written until someone asks for it.
     */
    private void send(UserResponse account, NotificationType type, NotificationChannel channel,
                      String title, String message)
    {
        var notification = new Notification();

        notification.setAppUserId(account.appUserId());
        notification.setNotificationType(type);
        notification.setChannel(channel);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setCreatedAt(Instant.now());

        notificationRepository.save(notification);

        if (channel == NotificationChannel.EMAIL)
        {
            deliverByMail(notification, account, title, message);

            return;
        }

        // IN_APP needs no carrier, and there is no SMS provider: that channel would
        // only reach the log (04-Base-de-Datos §6).
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());
    }

    private void deliverByMail(Notification notification, UserResponse account, String title, String message)
    {
        if (account.email() == null || account.email().isBlank())
        {
            fail(notification, "La cuenta no tiene un correo registrado.");

            return;
        }

        var mail = new SimpleMailMessage();

        mail.setTo(account.email());
        mail.setSubject("Fitness App · " + title);
        mail.setText(message);

        try
        {
            mailSender.send(mail);

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
        }
        catch (Exception ex)
        {
            log.warn("Mail delivery failed for notificationId={} type={}: {}",
                     notification.getNotificationId(), notification.getNotificationType(), ex.getMessage(), ex);

            fail(notification, ex.getMessage());
        }
    }

    /** failure_reason is VARCHAR(300): a driver stack trace overflows it easily. */
    private static void fail(Notification notification, String reason)
    {
        var safeReason = reason == null ? "Error desconocido al entregar el aviso." : reason;

        notification.setStatus(NotificationStatus.FAILED);
        notification.setFailureReason(safeReason.length() > FAILURE_REASON_MAX
                                              ? safeReason.substring(0, FAILURE_REASON_MAX)
                                              : safeReason);
    }
}
