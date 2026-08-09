package com.fitness.app.access;

import com.fitness.app.access.dto.GuestPassRequest;
import com.fitness.app.access.model.GuestPass;
import com.fitness.app.access.model.GuestPassType;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.PersonService;
import com.fitness.app.directory.dto.PersonContactDTO;
import com.fitness.app.directory.dto.PersonRequestDTO;
import com.fitness.app.directory.model.DocumentType;
import com.fitness.app.directory.model.Person;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Guest pass rules. Only FREE_TRIAL is limited to one per person. PAID_DAY_PASS
 * and MEMBER_GUEST are unlimited (the latter requires a host).
 */
@ExtendWith(MockitoExtension.class)
class GuestPassServiceTest
{
    private static final Long PERSON_ID = 7L;
    private static final Long USER_ID   = 1L;

    @Mock private GuestPassRepository guestPassRepository;
    @Mock private PersonService       personService;

    @InjectMocks private GuestPassService guestPassService;

    @Test
    void refusesASecondFreeTrial()
    {
        var personData = personRequest();
        var person     = person();

        when(personService.createOrReuse(personData)).thenReturn(person);
        when(guestPassRepository.countByPersonIdAndPassType(PERSON_ID, GuestPassType.FREE_TRIAL)).thenReturn(1L);

        assertEquals(ErrorCode.GUEST_PASS_LIMIT_REACHED,
                     assertThrows(BusinessException.class,
                                  () -> guestPassService.create(
                                          new GuestPassRequest(personData, GuestPassType.FREE_TRIAL, null, null),
                                          principal()))
                             .getErrorCode());
    }

    @Test
    void allowsAPaidDayPassOfThePersonWhoHadAFreeTrial()
    {
        var personData = personRequest();
        var person     = person();

        when(personService.createOrReuse(personData)).thenReturn(person);
        when(guestPassRepository.save(any(GuestPass.class))).thenAnswer(call -> call.getArgument(0));
        when(personService.findContact(PERSON_ID)).thenReturn(personContact());

        var response = guestPassService.create(
                new GuestPassRequest(personData, GuestPassType.PAID_DAY_PASS, null, null),
                principal());

        assertEquals(GuestPassType.PAID_DAY_PASS, response.passType());
    }

    @Test
    void refusesMemberGuestWithoutHost()
    {
        var personData = personRequest();
        var person     = person();

        when(personService.createOrReuse(personData)).thenReturn(person);

        assertEquals(ErrorCode.VALIDATION_ERROR,
                     assertThrows(BusinessException.class,
                                  () -> guestPassService.create(
                                          new GuestPassRequest(personData, GuestPassType.MEMBER_GUEST, null, null),
                                          principal()))
                             .getErrorCode());
    }

    private static PersonRequestDTO personRequest()
    {
        return new PersonRequestDTO(
                DocumentType.PASSPORT,
                "ABC123456",
                "Juan",
                "Pérez",
                null,
                LocalDate.of(1990, 1, 15),
                "juan@test.com",
                "12345678",
                null
        );
    }

    private static Person person()
    {
        var person = new Person();

        person.setPersonId(PERSON_ID);
        person.setDocumentNumber("ABC123456");
        person.setFirstName("Juan");
        person.setLastName("Pérez");

        return person;
    }

    private static PersonContactDTO personContact()
    {
        return new PersonContactDTO(PERSON_ID, "Juan Pérez", "juan@test.com", "12345678");
    }

    private static AuthenticatedUser principal()
    {
        return new AuthenticatedUser(USER_ID, "recepcion", UserRole.RECEPTIONIST);
    }
}
