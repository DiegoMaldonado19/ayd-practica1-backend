package com.fitness.app.directory.model;

/**
 * Mirrors ck_member_status. This is the state of the *file*, not of the
 * membership contract: WITHDRAWN is the logical deletion, because erasing the row
 * would destroy the payment history that points at it.
 */
public enum MemberStatus
{
    ACTIVE,
    INACTIVE,
    WITHDRAWN
}
