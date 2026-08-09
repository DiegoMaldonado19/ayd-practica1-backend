package com.fitness.app.directory.dto;

/** What another module is allowed to know about a person: who they are and how to reach them. */
public record PersonContactDTO(Long   personId,
                               String fullName,
                               String email,
                               String phone)
{
}
