package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * The identity block shared by the four create/update payloads of member and
 * employee: one definition of what a person looks like on the way in.
 *
 * @Past is what enforces "birth date not in the future": schema.sql leaves that
 * rule to the service on purpose, because CURRENT_DATE inside a CHECK is not
 * immutable. The sizes mirror the VARCHAR lengths of the person table so the
 * answer is a 400 with the offending field and not a 500 from the driver.
 */
public record PersonRequestDTO(@NotNull                DocumentType documentType,
                               @NotBlank @Size(max = 25) String     documentNumber,
                               @NotBlank @Size(max = 50) String     firstName,
                               @NotBlank @Size(max = 50) String     lastName,
                               @Size(max = 20)           String     gender,
                               @Past                     LocalDate  birthDate,
                               @Email @Size(max = 120)   String     email,
                               @Size(max = 20)           String     phone,
                               @Size(max = 200)          String     address)
{
}
