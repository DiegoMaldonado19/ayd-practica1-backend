package com.fitness.app.nutrition.model;

/**
 * How the consumed calories of a day compare to the goal: below, inside the
 * tolerance band (±tolerance_percent) or above it.
 */
public enum CalorieStatus
{
    UNDER, ACCEPTABLE, OVER
}