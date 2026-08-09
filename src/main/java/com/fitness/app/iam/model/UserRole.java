package com.fitness.app.iam.model;

/** The four profiles the statement defines. Fixed set, so an enum and not an RBAC of four tables. */
public enum UserRole
{
    ADMIN,
    RECEPTIONIST,
    TRAINER,
    MEMBER
}
