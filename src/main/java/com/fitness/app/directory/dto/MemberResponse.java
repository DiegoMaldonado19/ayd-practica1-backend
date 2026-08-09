package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.Member;
import com.fitness.app.directory.model.MemberStatus;

import java.time.LocalDate;

/**
 * The member file as the interface sees it. Serves the listing, the detail and
 * both writes: four endpoints, one shape.
 *
 * Field names are lowerCamelCase and Jackson renders member_code and
 * emergency_contact_name from the global SNAKE_CASE strategy.
 */
public record MemberResponse(Long         memberId,
                             PersonDTO    person,
                             String       memberCode,
                             LocalDate    joinedOn,
                             MemberStatus status,
                             String       emergencyContactName,
                             String       emergencyContactPhone,
                             LocalDate    terminatedOn,
                             String       notes)
{
    public static MemberResponse from(Member member)
    {
        return new MemberResponse(member.getMemberId(),
                                  PersonDTO.from(member.getPerson()),
                                  member.getMemberCode(),
                                  member.getJoinedOn(),
                                  member.getStatus(),
                                  member.getEmergencyContactName(),
                                  member.getEmergencyContactPhone(),
                                  member.getTerminatedOn(),
                                  member.getNotes());
    }
}
