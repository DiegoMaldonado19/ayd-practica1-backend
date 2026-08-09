package com.fitness.app.directory.model;

/**
 * Mirrors ck_employee_pos. What the person does at the gym, which is not the same
 * as the role they sign in with: that one lives in app_user.role.
 */
public enum EmployeePosition
{
    ADMIN,
    RECEPTIONIST,
    TRAINER
}
