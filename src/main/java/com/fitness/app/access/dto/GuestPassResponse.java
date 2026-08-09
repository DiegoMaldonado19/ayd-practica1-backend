package com.fitness.app.access.dto;

import com.fitness.app.access.model.GuestPass;
import com.fitness.app.access.model.GuestPassType;
import com.fitness.app.directory.dto.PersonContactDTO;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Guest pass as the interface sees it. Nests the PersonContactDTO.
 */
public record GuestPassResponse(
    @JsonProperty("guest_pass_id")
    Long             guestPassId,

    PersonContactDTO person,

    @JsonProperty("pass_type")
    GuestPassType    passType,

    @JsonProperty("host_member_id")
    Long             hostMemberId,

    @JsonProperty("checked_in_at")
    Instant          checkedInAt,

    String           notes
)
{
    public static GuestPassResponse from(GuestPass guestPass, PersonContactDTO person)
    {
        return new GuestPassResponse(
            guestPass.getGuestPassId(),
            person,
            guestPass.getPassType(),
            guestPass.getHostMemberId(),
            guestPass.getCheckedInAt(),
            guestPass.getNotes()
        );
    }
}
