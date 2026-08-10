package com.fitness.app.classes.model;

/** ck_session_status: the state machine of a dated occurrence, driven by PATCH .../status. */
public enum SessionStatus
{
    SCHEDULED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
