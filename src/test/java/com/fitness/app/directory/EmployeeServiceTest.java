package com.fitness.app.directory;

import com.fitness.app.directory.dto.CreateEmployeeRequest;
import com.fitness.app.directory.dto.PersonRequestDTO;
import com.fitness.app.directory.model.DocumentType;
import com.fitness.app.directory.model.Employee;
import com.fitness.app.directory.model.EmployeePosition;
import com.fitness.app.directory.model.EmployeeStatus;
import com.fitness.app.directory.model.Person;
import com.fitness.app.directory.model.Trainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The alta of an employee is the only place a trainer row is born, and the
 * termination of one is the only place it is switched off. Neither is visible
 * from the Postman response alone.
 */
@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest
{
    private static final Long  PERSON_ID   = 5L;
    private static final Long  EMPLOYEE_ID = 2L;
    private static final short DEFAULT_LOAD = 20;

    @Mock private EmployeeRepository employeeRepository;
    @Mock private TrainerRepository  trainerRepository;
    @Mock private PersonService      personService;

    @InjectMocks private EmployeeService employeeService;

    @Test
    void opensTheTrainerProfileWithTheDefaultLoadWhenNoneIsGiven()
    {
        var saved = ArgumentCaptor.forClass(Trainer.class);

        stubAlta();
        when(trainerRepository.save(saved.capture())).thenAnswer(call -> call.getArgument(0));

        var created = employeeService.create(request(EmployeePosition.TRAINER, null));

        assertEquals("EMP-0005", created.employeeCode());
        assertEquals(DEFAULT_LOAD, saved.getValue().getMaxMemberLoad());
        assertTrue(saved.getValue().isActive());
    }

    @Test
    void leavesAReceptionistWithoutATrainerProfile()
    {
        stubAlta();

        assertNull(employeeService.create(request(EmployeePosition.RECEPTIONIST, (short) 15)).trainerId());
        verify(trainerRepository, never()).save(any(Trainer.class));
    }

    @Test
    void stopsATerminatedTrainerFromTakingMembers()
    {
        var employee = employee();
        var trainer  = trainer(employee);

        when(employeeRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        var terminated = employeeService.changeStatus(EMPLOYEE_ID, EmployeeStatus.TERMINATED);

        assertEquals(LocalDate.now(), terminated.terminatedOn());
        assertFalse(trainer.isActive());
    }

    private void stubAlta()
    {
        when(personService.createOrReuse(any())).thenReturn(person());
        when(employeeRepository.existsByPerson_PersonId(PERSON_ID)).thenReturn(false);
        when(employeeRepository.save(any(Employee.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static CreateEmployeeRequest request(EmployeePosition position, Short maxMemberLoad)
    {
        return new CreateEmployeeRequest(new PersonRequestDTO(DocumentType.DPI, "1000000000103", "Marta",
                                                              "Reyes", null, LocalDate.of(1990, 1, 20),
                                                              "marta@fitnessapp.local", "55550003", null),
                                         position, LocalDate.now(), maxMemberLoad, "Entrenadora funcional");
    }

    private static Person person()
    {
        var person = new Person();

        person.setPersonId(PERSON_ID);
        person.setDocumentType(DocumentType.DPI);
        person.setDocumentNumber("1000000000103");
        person.setFirstName("Marta");
        person.setLastName("Reyes");

        return person;
    }

    private static Employee employee()
    {
        var employee = new Employee();

        employee.setEmployeeId(EMPLOYEE_ID);
        employee.setPerson(person());
        employee.setEmployeeCode("EMP-0005");
        employee.setPosition(EmployeePosition.TRAINER);
        employee.setHiredOn(LocalDate.now());
        employee.setStatus(EmployeeStatus.ACTIVE);

        return employee;
    }

    /** Wires both sides: changeStatus reaches the profile through employee.getTrainer(). */
    private static Trainer trainer(Employee employee)
    {
        var trainer = new Trainer();

        trainer.setTrainerId(9L);
        trainer.setEmployee(employee);
        trainer.setMaxMemberLoad(DEFAULT_LOAD);
        trainer.setActive(true);
        employee.setTrainer(trainer);

        return trainer;
    }
}
