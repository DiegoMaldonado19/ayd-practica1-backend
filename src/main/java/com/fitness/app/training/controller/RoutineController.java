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

@RestController
@RequestMapping("/api/v1/routines")
@RequiredArgsConstructor
public class RoutineController
{
    private final RoutineService routineService;

    @GetMapping
    /**
     * Lista rutinas con filtros por miembro, entrenador y estado.
     * El alcance de la búsqueda depende del rol del usuario autenticado.
     *
     * @param memberId  id del socio (opcional)
     * @param trainerId id del entrenador (opcional)
     * @param status    estado de la rutina (opcional)
     * @param principal usuario autenticado que realiza la consulta
     * @param pageable  paginación
     * @return página de RoutineResponse que coinciden con los filtros
     */
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
    /**
     * Devuelve el detalle de una rutina por su id, validando permisos según el socio asociado.
     *
     * @param routineId id de la rutina
     * @param principal usuario autenticado que realiza la consulta
     * @return RoutineResponse con el detalle de la rutina
     */
    public RoutineResponse detail(@PathVariable Long routineId,
                                  @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return routineService.findById(routineId, principal);
    }

    @PutMapping("/{routineId}")
    /**
     * Reemplaza completamente una rutina y sus ejercicios asociados.
     * Solo el autor (entrenador) puede modificarla.
     *
     * @param routineId id de la rutina a reemplazar
     * @param request   nueva definición de la rutina
     * @param principal usuario autenticado que realiza la acción
     * @return la rutina actualizada
     */
    public RoutineResponse update(@PathVariable Long routineId,
                                  @Valid @RequestBody RoutineRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return routineService.update(routineId, request, principal);
    }

    @PatchMapping("/{routineId}/status")
    /**
     * Cambia el estado de la rutina (p. ej. PUBLISHED o ARCHIVED).
     * Las transiciones inválidas se rechazan.
     *
     * @param routineId id de la rutina
     * @param request   nuevo estado solicitado
     * @param principal usuario autenticado que realiza la acción
     * @return la rutina con el estado actualizado
     */
    public RoutineResponse changeStatus(@PathVariable Long routineId,
                                        @Valid @RequestBody RoutineStatusRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return routineService.changeStatus(routineId, request, principal);
    }
}