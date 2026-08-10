package com.fitness.app.notification.model;

/** ck_notification_stat: PENDING -> SENT -> READ, or FAILED off that path. */
public enum NotificationStatus
{
    PENDING,
    SENT,
    FAILED,
    READ
}
