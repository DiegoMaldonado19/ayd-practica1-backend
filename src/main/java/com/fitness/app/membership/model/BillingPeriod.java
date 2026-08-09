package com.fitness.app.membership.model;

import lombok.Getter;

/**
 * Duration of a contract. The days of each period live here and not in a column of
 * membership_plan: storing them would be a derived dependency (04-Base-de-Datos §5).
 */
@Getter
public enum BillingPeriod
{
    MONTHLY   (30),
    QUARTERLY (90),
    SEMIANNUAL(180),
    ANNUAL    (365);

    private final int days;

    BillingPeriod(int days)
    {
        this.days = days;
    }
}
