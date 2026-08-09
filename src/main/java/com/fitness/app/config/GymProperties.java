package com.fitness.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The tunable rules of the gym, read from the gym.* block of application.yml. They
 * are configuration and not constants because the statement asks the team to define
 * and document them: "el sistema debe limitar la cantidad de veces o de días que una
 * membresía puede congelarse por ciclo (por ejemplo, máximo 15 días acumulados por
 * trimestre)".
 *
 * Only the freeze block is bound: each module adds its own as it arrives.
 */
@ConfigurationProperties(prefix = "gym")
public record GymProperties(Freeze freeze)
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
}
