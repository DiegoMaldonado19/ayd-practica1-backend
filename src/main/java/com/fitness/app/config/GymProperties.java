package com.fitness.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The tunable rules of the gym, read from the gym.* block of application.yml. They
 * are configuration and not constants because the statement asks the team to define
 * and document them: "el sistema debe limitar la cantidad de veces o de días que una
 * membresía puede congelarse por ciclo (por ejemplo, máximo 15 días acumulados por
 * trimestre)".
 */
@ConfigurationProperties(prefix = "gym")
public record GymProperties(Freeze freeze, GuestPass guestPass, Classes classes)
{
    /**
     * cycleDays is the window the limits are measured over - 90 days, the "trimestre"
     * of the statement.
     */
    public record Freeze(int maxDaysPerCycle,
                         int maxCountPerCycle,
                         int cycleDays)
    {
    }

    /** Guest pass limits. */
    public record GuestPass(int maxFreePerPerson)
    {
    }

    /**
     * "Con cierta anticipación (por ejemplo, más de 2 horas antes)" and the waitlist
     * confirmation window, both left to the team to define (02-Modulos §2.6).
     *
     * Named Classes and not Class: class is a Java reserved word and cannot be a
     * record component.
     */
    public record Classes(int cancellationWindowHours,
                          int waitlistConfirmationMinutes)
    {
    }
}
