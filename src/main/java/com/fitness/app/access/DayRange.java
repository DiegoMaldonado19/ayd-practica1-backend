package com.fitness.app.access;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Converts LocalDate range parameters (from/to) into Instant sentinels for the
 * database. PostgreSQL cannot infer types for IS NULL in temporal contexts, so
 * sentinels replace null parameters.
 *
 * The sentinels are calendar dates and not Instant.MIN/MAX: those two overflow the
 * conversion to TIMESTAMPTZ, so a listing with no date filter answered 500 instead of
 * the whole history. Same upper bound as MembershipService.NO_EXPIRY_FILTER.
 */
class DayRange
{
    private static final Instant NO_LOWER_BOUND = LocalDate.of(1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
    private static final Instant NO_UPPER_BOUND = LocalDate.of(9999, 12, 31).atStartOfDay(ZoneOffset.UTC).toInstant();

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
            : NO_LOWER_BOUND;

        var toInstant   = to != null
            ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()
            : NO_UPPER_BOUND;

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
