package com.fitness.app.directory.model;

/**
 * Mirrors ck_trn_spec_value. The first three are literal in the statement
 * ("perdida de peso, ganancia muscular o rehabilitacion"); the last two are the
 * documented extension.
 */
public enum Specialty
{
    WEIGHT_LOSS,
    MUSCLE_GAIN,
    REHABILITATION,
    FUNCTIONAL,
    CARDIO
}
