package com.fitness.app.classes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/** "Se une a la lista de espera" (§3.6). */
public record WaitlistRequest(
    @NotNull
    @JsonProperty("member_id")
    Long memberId
)
{
}
