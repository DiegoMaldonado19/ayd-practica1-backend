package com.fitness.app.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * The business error catalog: the contract between GlobalExceptionHandler and the
 * frontend error translator. suggestedAction tells the interface which button to
 * offer, and is null when there is nothing useful to suggest.
 *
 * Messages are in Spanish because they are user-facing text; every identifier
 * stays in English.
 */
@Getter
public enum ErrorCode
{
    // --- Authentication and authorization -----------------------------------
    INVALID_CREDENTIALS           (HttpStatus.UNAUTHORIZED,        null,                      "Usuario o contraseña incorrectos."),
    UNAUTHENTICATED               (HttpStatus.UNAUTHORIZED,        null,                      "Debes iniciar sesión para acceder a este recurso."),
    ACCOUNT_BLOCKED               (HttpStatus.FORBIDDEN,           "CONTACT_ADMIN",           "La cuenta está bloqueada por demasiados intentos fallidos."),
    ACCOUNT_INACTIVE              (HttpStatus.FORBIDDEN,           "CONTACT_ADMIN",           "La cuenta está desactivada o pendiente de activación."),
    VERIFICATION_CODE_INVALID     (HttpStatus.BAD_REQUEST,         "RESEND_CODE",             "El código de verificación no es correcto."),
    VERIFICATION_CODE_EXPIRED     (HttpStatus.BAD_REQUEST,         "RESEND_CODE",             "El código de verificación ya venció."),
    VERIFICATION_ATTEMPTS_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS,   "RESEND_CODE",             "Demasiados intentos con el mismo código. Solicita uno nuevo."),
    VERIFICATION_DESTINATION_MISSING(HttpStatus.CONFLICT,          "CONTACT_ADMIN",           "La cuenta no tiene un correo o teléfono registrado para enviar el código."),
    FORBIDDEN_RESOURCE            (HttpStatus.FORBIDDEN,           null,                      "Tu rol no tiene acceso a este recurso."),
    TRAINER_SCOPE_VIOLATION       (HttpStatus.FORBIDDEN,           null,                      "Solo puedes consultar datos de los socios que tienes asignados."),
    USERNAME_TAKEN                (HttpStatus.CONFLICT,            null,                      "El nombre de usuario ya está en uso."),
    PERSON_ALREADY_HAS_ACCOUNT    (HttpStatus.CONFLICT,            null,                      "La persona ya tiene una cuenta."),

    // --- Directory ------------------------------------------------------------
    DOCUMENT_ALREADY_REGISTERED   (HttpStatus.CONFLICT,            null,                      "Ya existe un expediente con ese documento."),

    // --- Membership and access ----------------------------------------------
    MEMBERSHIP_NOT_ACTIVE         (HttpStatus.CONFLICT,            "RENEW_MEMBERSHIP",        "La membresía no está activa."),
    MEMBERSHIP_FROZEN             (HttpStatus.CONFLICT,            "REACTIVATE_MEMBERSHIP",   "La membresía está congelada."),
    MEMBERSHIP_EXPIRED            (HttpStatus.CONFLICT,            "RENEW_MEMBERSHIP",        "La membresía venció."),
    MEMBERSHIP_CANCELLED          (HttpStatus.CONFLICT,            "CONTACT_FRONT_DESK",      "La membresía está cancelada."),
    MEMBERSHIP_ALREADY_ACTIVE     (HttpStatus.CONFLICT,            null,                      "El socio ya tiene un contrato vigente."),
    FREEZE_LIMIT_REACHED          (HttpStatus.CONFLICT,            null,                      "Se superó el límite de congelamientos del ciclo."),
    FREEZE_NOT_IN_PROGRESS        (HttpStatus.CONFLICT,            null,                      "La membresía no está congelada."),
    INVALID_STATE_TRANSITION      (HttpStatus.CONFLICT,            null,                      "El cambio de estado solicitado no está permitido."),
    VISIT_ALREADY_OPEN            (HttpStatus.CONFLICT,            null,                      "El socio ya hizo check-in y no ha salido."),
    GUEST_PASS_LIMIT_REACHED      (HttpStatus.CONFLICT,            "OFFER_MEMBERSHIP",        "La persona ya utilizó su pase gratuito."),

    // --- Classes -------------------------------------------------------------
    PLAN_BENEFIT_NOT_INCLUDED     (HttpStatus.FORBIDDEN,           "UPGRADE_PLAN",            "Tu plan no incluye este beneficio."),
    SEAT_UNAVAILABLE              (HttpStatus.CONFLICT,            "JOIN_WAITLIST",           "La clase alcanzó su cupo máximo."),
    WEEKLY_LIMIT_REACHED          (HttpStatus.CONFLICT,            "UPGRADE_PLAN",            "Ya consumiste las clases que tu plan permite esta semana."),
    ALREADY_ENROLLED              (HttpStatus.CONFLICT,            null,                      "Ya estás inscrito en esta sesión."),
    ALREADY_IN_WAITLIST           (HttpStatus.CONFLICT,            null,                      "Ya estás en la lista de espera de esta sesión."),
    CANCELLATION_WINDOW_CLOSED    (HttpStatus.CONFLICT,            null,                      "Se venció el margen para cancelar la inscripción."),
    SESSION_CANCELLED             (HttpStatus.CONFLICT,            null,                      "La sesión fue cancelada."),
    SESSION_NOT_OPEN              (HttpStatus.CONFLICT,            null,                      "La clase todavía no ha iniciado."),
    WAITLIST_CONFIRMATION_EXPIRED (HttpStatus.CONFLICT,            null,                      "Se venció la ventana para confirmar el cupo liberado."),
    RATING_REQUIRES_ATTENDANCE    (HttpStatus.FORBIDDEN,           null,                      "Solo puedes calificar una clase a la que asististe."),
    TRAINER_SCHEDULE_CONFLICT     (HttpStatus.CONFLICT,            null,                      "El entrenador ya tiene otra clase en ese horario."),

    // --- Training and nutrition ----------------------------------------------
    TRAINER_CAPACITY_EXCEEDED     (HttpStatus.CONFLICT,            "CHOOSE_ANOTHER_TRAINER",  "El entrenador alcanzó su carga máxima de socios."),
    TRAINER_ALREADY_ASSIGNED      (HttpStatus.CONFLICT,            null,                      "El socio ya tiene un entrenador asignado."),
    TRANSFER_TARGET_INVALID       (HttpStatus.CONFLICT,            "CHOOSE_ANOTHER_TRAINER",  "El entrenador destino no tiene capacidad para recibir la cartera."),
    MEAL_EDIT_WINDOW_CLOSED       (HttpStatus.CONFLICT,            null,                      "Solo puedes editar comidas registradas el mismo día."),
    MEASUREMENT_DUPLICATE_DATE    (HttpStatus.CONFLICT,            null,                      "Ya existe una medición de este socio en esa fecha."),
    FOOD_IN_USE                   (HttpStatus.CONFLICT,            "DEACTIVATE_INSTEAD",      "El alimento ya se usó en comidas registradas."),

    // --- Billing --------------------------------------------------------------
    PROMOTION_NOT_APPLICABLE      (HttpStatus.CONFLICT,            null,                      "La promoción está vencida, inactiva o no aplica a este plan."),
    PROMOTION_USES_EXCEEDED       (HttpStatus.CONFLICT,            null,                      "Se agotaron los usos de la promoción."),
    PAYMENT_ALREADY_CONFIRMED     (HttpStatus.CONFLICT,            null,                      "El pago ya fue confirmado."),
    PAYMENT_ALREADY_VOIDED        (HttpStatus.CONFLICT,            null,                      "El pago ya fue anulado."),

    // --- Not found ------------------------------------------------------------
    // One per entity that already exists in code. Each module adds its own.
    ROUTE_NOT_FOUND               (HttpStatus.NOT_FOUND,           null,                      "La ruta solicitada no existe."),
    USER_NOT_FOUND                (HttpStatus.NOT_FOUND,           null,                      "La cuenta no existe."),
    PERSON_NOT_FOUND              (HttpStatus.NOT_FOUND,           null,                      "La persona no existe."),
    MEMBER_NOT_FOUND              (HttpStatus.NOT_FOUND,           null,                      "El socio no existe."),
    MEMBERSHIP_NOT_FOUND          (HttpStatus.NOT_FOUND,           null,                      "La membresía no existe."),
    MEMBERSHIP_PLAN_NOT_FOUND     (HttpStatus.NOT_FOUND,           null,                      "El plan de membresía no existe."),
    EMPLOYEE_NOT_FOUND            (HttpStatus.NOT_FOUND,           null,                      "El empleado no existe."),
    TRAINER_NOT_FOUND             (HttpStatus.NOT_FOUND,           null,                      "El entrenador no existe."),
    VERIFICATION_CODE_NOT_FOUND   (HttpStatus.NOT_FOUND,           null,                      "El código de verificación no existe."),
    VISIT_NOT_FOUND               (HttpStatus.NOT_FOUND,           null,                      "La visita no existe."),
    GROUP_CLASS_NOT_FOUND         (HttpStatus.NOT_FOUND,           null,                      "La clase grupal no existe."),
    CLASS_SESSION_NOT_FOUND       (HttpStatus.NOT_FOUND,           null,                      "La sesión de clase no existe."),
    ENROLLMENT_NOT_FOUND          (HttpStatus.NOT_FOUND,           null,                      "La inscripción no existe."),
    WAITLIST_ENTRY_NOT_FOUND      (HttpStatus.NOT_FOUND,           null,                      "La entrada en la lista de espera no existe."),

    // --- Transversal ----------------------------------------------------------
    VALIDATION_ERROR              (HttpStatus.BAD_REQUEST,         null,                      "Hay campos inválidos en la solicitud."),
    INTERNAL_ERROR                (HttpStatus.INTERNAL_SERVER_ERROR, null,                    "Ocurrió un error inesperado. Reporta el identificador de traza.");

    private final HttpStatus httpStatus;
    private final String     suggestedAction;
    private final String     message;

    ErrorCode(HttpStatus httpStatus, String suggestedAction, String message)
    {
        this.httpStatus      = httpStatus;
        this.suggestedAction = suggestedAction;
        this.message         = message;
    }
}
