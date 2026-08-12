package com.fitness.app.nutrition.controller;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.nutrition.dto.MealRequest;
import com.fitness.app.nutrition.dto.MealResponse;
import com.fitness.app.nutrition.dto.MealUpdateRequest;
import com.fitness.app.nutrition.service.MealService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Maneja la creación, consulta y edición de comidas del usuario.
 * Cada miembro solo puede operar sobre sus propias comidas en el mismo día.
 */
@RestController
@RequestMapping("/api/v1/meals")
@RequiredArgsConstructor
public class MealController
{
    private final MealService mealService;

    /** Busca comidas por miembro, rango de fechas y filtros adicionales. */
    @GetMapping
    public PagedModel<MealResponse> search(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal AuthenticatedUser principal,
            Pageable pageable)
    {
        return new PagedModel<>(mealService.search(memberId, date, from, to, principal, pageable));
    }

    /** Registra una nueva comida con sus alimentos y cantidades. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MealResponse create(@Valid @RequestBody MealRequest request,
                               @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return mealService.create(request, principal);
    }

    /** Obtiene una comida por identificador, validando el acceso del usuario. */
    @GetMapping("/{mealId}")
    public MealResponse findById(@PathVariable Long mealId,
                                 @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return mealService.findById(mealId, principal);
    }

    /** Actualiza una comida existente si está dentro del período permitido. */
    @PutMapping("/{mealId}")
    public MealResponse update(@PathVariable Long mealId,
                               @Valid @RequestBody MealUpdateRequest request,
                               @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return mealService.update(mealId, request, principal);
    }

    /** Elimina una comida del día actual, siempre que el usuario tenga permiso. */
    @DeleteMapping("/{mealId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long mealId,
                       @AuthenticationPrincipal AuthenticatedUser principal)
    {
        mealService.delete(mealId, principal);
    }
}