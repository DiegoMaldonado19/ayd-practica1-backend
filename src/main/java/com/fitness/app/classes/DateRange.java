package com.fitness.app.classes;

import java.time.LocalDate;

/**
 * Normalizes nullable from/to date filters into sentinels PostgreSQL can bind, mirroring
 * access.DayRange. session_date is a plain DATE, so the sentinels stay in LocalDate
 * space instead of converting to Instant.
 *
 * The sentinels are calendar dates and not LocalDate.MIN/MAX: those two fall outside the
 * range of the DATE type and PostgreSQL answers "date out of range". Same reasoning, and
 * same upper bound, as MembershipService.NO_EXPIRY_FILTER.
 */
record DateRange(LocalDate from, LocalDate to)
{
    private static final LocalDate NO_LOWER_BOUND = LocalDate.of(1, 1, 1);
    private static final LocalDate NO_UPPER_BOUND = LocalDate.of(9999, 12, 31);

    static DateRange parse(LocalDate from, LocalDate to)
    {
        return new DateRange(from == null ? NO_LOWER_BOUND : from,
                             to == null ? NO_UPPER_BOUND : to);
    }
}
