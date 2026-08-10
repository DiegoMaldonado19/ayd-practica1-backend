package com.fitness.app.notification.model;

/**
 * ck_notification_type: one value per notifiable fact of the statement.
 *
 * The SESSION_ prefix and not CLASS_ because what the administrator cancels or
 * reschedules is one dated class_session, never the recurring group_class
 * (04-Base-de-Datos §6).
 *
 * TRAINER_ALERT and PAYMENT_CONFIRMED are declared so the enum mirrors the check
 * constraint in full, but nothing emits them yet: they belong to training and
 * billing, which do not exist. Their sender arrives with their module.
 */
public enum NotificationType
{
    MEMBERSHIP_EXPIRING,
    MEMBERSHIP_EXPIRED,
    MEMBERSHIP_FROZEN,
    MEMBERSHIP_REACTIVATED,
    WAITLIST_SEAT_AVAILABLE,
    SESSION_CANCELLED,
    SESSION_RESCHEDULED,
    TRAINER_ASSIGNED,
    TRAINER_ALERT,
    PAYMENT_CONFIRMED
}
