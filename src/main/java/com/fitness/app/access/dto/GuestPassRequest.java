package com.fitness.app.access.dto;

import com.fitness.app.access.model.GuestPassType;
import com.fitness.app.directory.dto.PersonRequestDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Guest pass request. Reuses PersonRequestDTO for identity.
 */
public record GuestPassRequest(
    @Valid
    @NotNull
    PersonRequestDTO person,

    @NotNull
    @JsonProperty("pass_type")
    GuestPassType    passType,

    @JsonProperty("host_member_id")
    Long             hostMemberId,

    String           notes
)
{
}
