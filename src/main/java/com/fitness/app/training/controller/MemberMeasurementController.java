package com.fitness.app.training.controller;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.dto.ProgressMeasurementRequest;
import com.fitness.app.training.dto.ProgressMeasurementResponse;
import com.fitness.app.training.service.ProgressMeasurementService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * "Mediciones de progreso. Filtros: from, to. Base de la gráfica de evolución" (§3.7).
 * The route hangs from /members, so the member is the one in the path.
 */
@RestController
@RequestMapping("/api/v1/members/{memberId}/measurements")
@RequiredArgsConstructor
public class MemberMeasurementController
{
    private final ProgressMeasurementService progressMeasurementService;

    @GetMapping
    /**
     * Devuelve el historial de mediciones de un socio en un rango de fechas.
     *
     * @param memberId  id del socio
     * @param from  fecha inicial del filtro (opcional)
     * @param to fecha final del filtro (opcional)
     * @param principal usuario autenticado que realiza la consulta
     * @return lista de mediciones ordenadas por fecha ascendente
     */
    public List<ProgressMeasurementResponse> history(@PathVariable Long memberId,
                                                     @RequestParam(required = false) LocalDate from,
                                                     @RequestParam(required = false) LocalDate to,
                                                     @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return progressMeasurementService.findByMember(memberId, from, to, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProgressMeasurementResponse create(@PathVariable Long memberId,
                                              @Valid @RequestBody ProgressMeasurementRequest request,
                                              @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return progressMeasurementService.create(memberId, request, principal);
    }
}