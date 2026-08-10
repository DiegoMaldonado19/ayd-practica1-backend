package com.fitness.app.classes.model;

/** ck_waitlist_status: WAITING -> NOTIFIED -> PROMOTED, or EXPIRED/CANCELLED off that path. */
public enum WaitlistStatus
{
    WAITING,
    NOTIFIED,
    PROMOTED,
    EXPIRED,
    CANCELLED
}
