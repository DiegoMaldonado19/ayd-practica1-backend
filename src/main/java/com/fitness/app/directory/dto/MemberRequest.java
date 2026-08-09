package com.fitness.app.directory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of both POST /members and PUT /members/{id}: the alta and the edit take
 * exactly the same fields, so they take the same record.
 *
 * It carries no credentials: the account is created afterwards with POST /users
 * against the person_id the alta returns. It carries no status either, because
 * only the administrator changes it and that has its own endpoint.
 */
public record MemberRequest(@NotNull @Valid  PersonRequestDTO person,
                            @Size(max = 100) String           emergencyContactName,
                            @Size(max = 20)  String           emergencyContactPhone,
                            @Size(max = 300) String           notes)
{
}
