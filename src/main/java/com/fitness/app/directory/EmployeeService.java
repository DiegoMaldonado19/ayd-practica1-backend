package com.fitness.app.directory;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.dto.CreateEmployeeRequest;
import com.fitness.app.directory.dto.EmployeeResponse;
import com.fitness.app.directory.dto.UpdateEmployeeRequest;
import com.fitness.app.directory.model.Employee;
import com.fitness.app.directory.model.EmployeePosition;
import com.fitness.app.directory.model.EmployeeStatus;
import com.fitness.app.directory.model.Trainer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * The staff file. The alta of §3.2 #10 also opens the trainer profile when the
 * position is TRAINER, because every training table points at trainer and not at
 * employee.
 *
 * It writes that row through TrainerRepository and not through TrainerService: no
 * service inside directory injects another, so no ordering between them can turn
 * into a bean cycle.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeService
{
    /** trainer.max_member_load DEFAULT: the request may leave it out. */
    private static final short DEFAULT_MEMBER_LOAD = 20;

    private final EmployeeRepository employeeRepository;
    private final TrainerRepository  trainerRepository;
    private final PersonService      personService;

    @Transactional(readOnly = true)
    public Page<EmployeeResponse> search(EmployeePosition position,
                                         EmployeeStatus   status,
                                         String           search,
                                         Pageable         pageable)
    {
        return employeeRepository.search(position, status, search == null ? "" : search, pageable)
                .map(EmployeeResponse::from);
    }

    @Transactional(readOnly = true)
    public EmployeeResponse findById(Long employeeId)
    {
        return EmployeeResponse.from(findOrFail(employeeId));
    }

    public EmployeeResponse create(CreateEmployeeRequest request)
    {
        var person = personService.createOrReuse(request.person());

        if (employeeRepository.existsByPerson_PersonId(person.getPersonId()))
        {
            throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_REGISTERED);
        }

        var employee = new Employee();

        employee.setPerson(person);
        employee.setEmployeeCode("EMP-%04d".formatted(person.getPersonId()));
        employee.setPosition(request.position());
        employee.setHiredOn(request.hiredOn());
        employee.setStatus(EmployeeStatus.ACTIVE);
        employeeRepository.save(employee);
        openTrainerProfile(employee, request);

        return EmployeeResponse.from(employee);
    }

    public EmployeeResponse update(Long employeeId, UpdateEmployeeRequest request)
    {
        var employee = findOrFail(employeeId);

        personService.apply(employee.getPerson(), request.person());
        employee.setHiredOn(request.hiredOn());

        return EmployeeResponse.from(employee);
    }

    public EmployeeResponse changeStatus(Long employeeId, EmployeeStatus status)
    {
        var employee = findOrFail(employeeId);

        employee.setStatus(status);
        employee.setTerminatedOn(status == EmployeeStatus.TERMINATED ? LocalDate.now() : null);

        // A terminated trainer stops taking members. Handing their caseload to
        // another trainer is training.transferCaseload, which arrives with that
        // module (02-Modulos §3).
        if (employee.getTrainer() != null)
        {
            employee.getTrainer().setActive(status == EmployeeStatus.ACTIVE);
        }

        return EmployeeResponse.from(employee);
    }

    private void openTrainerProfile(Employee employee, CreateEmployeeRequest request)
    {
        if (request.position() != EmployeePosition.TRAINER)
        {
            return;
        }

        var trainer = new Trainer();

        trainer.setEmployee(employee);
        trainer.setMaxMemberLoad(request.maxMemberLoad() == null ? DEFAULT_MEMBER_LOAD
                                                                 : request.maxMemberLoad());
        trainer.setBio(request.bio());
        trainer.setActive(true);

        // Set on both sides: the response is built from the entity, and the inverse
        // side is not refreshed by saving the owner.
        employee.setTrainer(trainerRepository.save(trainer));
    }

    private Employee findOrFail(Long employeeId)
    {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EMPLOYEE_NOT_FOUND));
    }
}
