package com.fitness.app.training.controller;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.dto.TrainerAlertRequest;
import com.fitness.app.training.dto.TrainerAlertResponse;
import com.fitness.app.training.dto.TrainerAlertStatusRequest;
import com.fitness.app.training.model.TrainerAlertStatus;
import com.fitness.app.training.service.TrainerAlertService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trainer-alerts")
@RequiredArgsConstructor
public class TrainerAlertController
{
    private final TrainerAlertService trainerAlertService;

    @GetMapping
    /**
     * Lista la cola de alertas filtrando por estado Los entrenadores ven solo sus
     *
     * @param status    estado de la alerta (opcional)
     * @param principal usuario autenticado que realiza la consulta
     * @param pageable  paginación
     * @return 
     */
    public PagedModel<TrainerAlertResponse> list(@RequestParam(required = false) TrainerAlertStatus status,
                                                 @AuthenticationPrincipal AuthenticatedUser principal,
                                                 Pageable pageable)
    {
        return new PagedModel<>(trainerAlertService.search(status, principal, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainerAlertResponse create(@Valid @RequestBody TrainerAlertRequest request,
                                       @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return trainerAlertService.create(request, principal);
    }

    @PatchMapping("/{alertId}/status")
    public TrainerAlertResponse changeStatus(@PathVariable Long alertId,
                                             @Valid @RequestBody TrainerAlertStatusRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return trainerAlertService.changeStatus(alertId, request, principal);
    }
}