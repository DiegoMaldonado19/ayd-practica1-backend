package com.fitness.app.access;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Converts LocalDate range parameters (from/to) into Instant sentinels for the
 * database. PostgreSQL cannot infer types for IS NULL in temporal contexts, so
 * sentinels (Instant.MIN and Instant.MAX) replace null parameters.
 */
class DayRange
{
    private final Instant from;
    private final Instant to;

    private DayRange(Instant from, Instant to)
    {
        this.from = from;
        this.to   = to;
    }

    static DayRange parse(LocalDate from, LocalDate to)
    {
        var fromInstant = from != null
            ? from.atStartOfDay(ZoneOffset.UTC).toInstant()
            : Instant.MIN;

        var toInstant   = to != null
            ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            : Instant.MAX;

        return new DayRange(fromInstant, toInstant);
    }

    Instant from()
    {
        return from;
    }

    Instant to()
    {
        return to;
    }
}
