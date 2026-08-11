package com.fitness.app.training.controller;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.dto.ProgressMeasurementRequest;
import com.fitness.app.training.dto.ProgressMeasurementResponse;
import com.fitness.app.training.service.ProgressMeasurementService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/measurements")
@RequiredArgsConstructor
public class MeasurementController
{
    private final ProgressMeasurementService progressMeasurementService;

    @PutMapping("/{measurementId}")
    /**
     * Corrige una medición existente identificada por measurementId
     * Solo el entrenador que registró la medición puede corregirla.
     *
     * @param measurementId id de la medición a corregir
     * @param request  datos con la corrección
     * @param principal usuario autenticado que realiza la acción
     * @return la medición corregida
     */
    public ProgressMeasurementResponse update(@PathVariable Long measurementId,
                                              @Valid @RequestBody ProgressMeasurementRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return progressMeasurementService.update(measurementId, request, principal);
    }

    @DeleteMapping("/{measurementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long measurementId,
                       @AuthenticationPrincipal AuthenticatedUser principal)
    {
        progressMeasurementService.delete(measurementId, principal);
    }
}