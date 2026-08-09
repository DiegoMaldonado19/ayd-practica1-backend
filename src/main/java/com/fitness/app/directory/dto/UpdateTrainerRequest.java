package com.fitness.app.directory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * The trainer profile: the member load cap of §3.2 #16, plus the bio the member
 * reads when choosing a trainer, which has no other write path.
 *
 * @Positive mirrors ck_trainer_load and @Max the SMALLINT range, so an out of
 * range value answers 400 with the field instead of 500 from the driver.
 */
public record UpdateTrainerRequest(@NotNull @Positive @Max(32767) Short  maxMemberLoad,
                                   @Size(max = 500)               String bio)
{
}
