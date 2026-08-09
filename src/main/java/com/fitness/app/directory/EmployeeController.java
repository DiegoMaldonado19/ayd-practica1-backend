package com.fitness.app.directory;

import com.fitness.app.directory.dto.CreateEmployeeRequest;
import com.fitness.app.directory.dto.EmployeeResponse;
import com.fitness.app.directory.dto.EmployeeStatusRequest;
import com.fitness.app.directory.dto.UpdateEmployeeRequest;
import com.fitness.app.directory.model.EmployeePosition;
import com.fitness.app.directory.model.EmployeeStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** The /employees routes. All of them belong to the administrator (SecurityConfig). */
@RestController
@RequestMapping("/api/v1/employees")
@RequiredArgsConstructor
public class EmployeeController
{
    private final EmployeeService employeeService;

    @GetMapping
    public PagedModel<EmployeeResponse> list(@RequestParam(required = false) EmployeePosition position,
                                             @RequestParam(required = false) EmployeeStatus   status,
                                             @RequestParam(required = false) String           search,
                                             Pageable                                         pageable)
    {
        return new PagedModel<>(employeeService.search(position, status, search, pageable));
    }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody CreateEmployeeRequest request)
    {
        var employee = employeeService.create(request);

        return ResponseEntity.created(URI.create("/api/v1/employees/" + employee.employeeId()))
                .body(employee);
    }

    @GetMapping("/{employeeId}")
    public EmployeeResponse detail(@PathVariable Long employeeId)
    {
        return employeeService.findById(employeeId);
    }

    @PutMapping("/{employeeId}")
    public EmployeeResponse update(@PathVariable Long employeeId,
                                   @Valid @RequestBody UpdateEmployeeRequest request)
    {
        return employeeService.update(employeeId, request);
    }

    @PatchMapping("/{employeeId}/status")
    public EmployeeResponse changeStatus(@PathVariable Long employeeId,
                                         @Valid @RequestBody EmployeeStatusRequest request)
    {
        return employeeService.changeStatus(employeeId, request.status());
    }
}
