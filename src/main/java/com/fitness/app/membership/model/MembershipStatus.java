package com.fitness.app.membership.model;

/** The four states of the statement. Only ACTIVE enables check-in, classes and nutrition. */
public enum MembershipStatus
{
    ACTIVE,
    FROZEN,
    EXPIRED,
    CANCELLED
}
