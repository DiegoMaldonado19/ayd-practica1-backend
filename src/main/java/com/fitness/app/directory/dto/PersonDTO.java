package com.fitness.app.directory.dto;

import com.fitness.app.directory.model.DocumentType;
import com.fitness.app.directory.model.Person;

import java.time.LocalDate;

/**
 * The full identity as the interface sees it, nested inside the member, employee
 * and trainer responses so the three share one shape.
 *
 * It does not replace PersonContactDTO: that one is the narrow contract other
 * modules consume (name, email, phone), this one is directory's own file view.
 */
public record PersonDTO(Long         personId,
                        DocumentType documentType,
                        String       documentNumber,
                        String       firstName,
                        String       lastName,
                        String       fullName,
                        String       gender,
                        LocalDate    birthDate,
                        String       email,
                        String       phone,
                        String       address)
{
    public static PersonDTO from(Person person)
    {
        return new PersonDTO(person.getPersonId(),
                             person.getDocumentType(),
                             person.getDocumentNumber(),
                             person.getFirstName(),
                             person.getLastName(),
                             person.getFullName(),
                             person.getGender(),
                             person.getBirthDate(),
                             person.getEmail(),
                             person.getPhone(),
                             person.getAddress());
    }
}
