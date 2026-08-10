package com.fitness.app.notification.model;

/**
 * ck_notification_chan: mail for the formal notices, the in-app inbox for everything.
 *
 * There is no SMS provider, so that channel is written to the application log instead
 * of being delivered (04-Base-de-Datos §6). iam's VerificationChannel is a different
 * set and is not reused.
 */
public enum NotificationChannel
{
    EMAIL,
    SMS,
    IN_APP
}
