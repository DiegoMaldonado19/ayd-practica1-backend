package com.fitness.app.directory;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.dto.PersonContactDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The public face of directory for the other modules. Today it serves iam, which
 * needs the address the verification code is sent to and the name shown in the
 * account listing.
 *
 * This is the whole reason no module maps Person on its own: the isolation rule
 * is that a module injects another module's Service, never its entities.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonService
{
    private final PersonRepository personRepository;

    public PersonContactDTO findContact(Long personId)
    {
        return personRepository.findById(personId)
                .map(PersonService::toContact)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERSON_NOT_FOUND));
    }

    /** Resolves a whole page of accounts in one query instead of one per row. */
    public Map<Long, PersonContactDTO> findContacts(Collection<Long> personIds)
    {
        if (personIds.isEmpty())
        {
            return Map.of();
        }

        return personRepository.findAllById(personIds).stream()
                .map(PersonService::toContact)
                .collect(Collectors.toMap(PersonContactDTO::personId, Function.identity()));
    }

    private static PersonContactDTO toContact(Person person)
    {
        return new PersonContactDTO(person.getPersonId(),
                                    person.getFirstName() + " " + person.getLastName(),
                                    person.getEmail(),
                                    person.getPhone());
    }
}
