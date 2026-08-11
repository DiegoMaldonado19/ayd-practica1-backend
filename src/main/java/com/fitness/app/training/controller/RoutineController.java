package com.fitness.app.training.controller;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.dto.RoutineRequest;
import com.fitness.app.training.dto.RoutineResponse;
import com.fitness.app.training.dto.RoutineStatusRequest;
import com.fitness.app.training.model.RoutineStatus;
import com.fitness.app.training.service.RoutineService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/** The /routines routes of §3.7: create, list, detail, replace and publish/archive. */
@RestController
@RequestMapping("/api/v1/routines")
@RequiredArgsConstructor
public class RoutineController
{
    private final RoutineService routineService;

    @GetMapping
    public PagedModel<RoutineResponse> list(@RequestParam(name = "member_id", required = false) Long memberId,
                                            @RequestParam(name = "trainer_id", required = false) Long trainerId,
                                            @RequestParam(required = false) RoutineStatus status,
                                            @AuthenticationPrincipal AuthenticatedUser principal,
                                            Pageable pageable)
    {
        return new PagedModel<>(routineService.search(memberId, trainerId, status, principal, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<RoutineResponse> create(@Valid @RequestBody RoutineRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser principal)
    {
        var routine = routineService.create(request, principal);

        return ResponseEntity.created(URI.create("/api/v1/routines/" + routine.routineId())).body(routine);
    }

    @GetMapping("/{routineId}")
    public RoutineResponse detail(@PathVariable Long routineId,
                                  @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return routineService.findById(routineId, principal);
    }

    @PutMapping("/{routineId}")
    public RoutineResponse update(@PathVariable Long routineId,
                                  @Valid @RequestBody RoutineRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return routineService.update(routineId, request, principal);
    }

    @PatchMapping("/{routineId}/status")
    public RoutineResponse changeStatus(@PathVariable Long routineId,
                                        @Valid @RequestBody RoutineStatusRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return routineService.changeStatus(routineId, request, principal);
    }
}