package com.fitness.app.classes.model;

/**
 * ck_enroll_status. ATTENDED and ABSENT double as attendance: "did the enrolled
 * member show up" is a status of the enrolment itself, not a separate table
 * (04-Base-de-Datos §6).
 */
public enum EnrollmentStatus
{
    ENROLLED,
    CANCELLED,
    ATTENDED,
    ABSENT
}
