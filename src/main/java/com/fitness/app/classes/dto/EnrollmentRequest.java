package com.fitness.app.classes.dto;

import com.fitness.app.access.model.AccessChannel;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

/**
 * "Inscribe. Valida membresía activa, beneficio del plan, límite semanal y cupo" (§3.6).
 * channel is optional and defaults to SELF_SERVICE; the receptionist sends FRONT_DESK
 * when enrolling a member in person.
 */
public record EnrollmentRequest(
    @NotNull
    @JsonProperty("member_id")
    Long          memberId,

    AccessChannel channel
)
{
}
