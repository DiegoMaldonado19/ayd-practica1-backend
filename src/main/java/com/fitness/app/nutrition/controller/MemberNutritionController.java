package com.fitness.app.nutrition.controller;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.nutrition.dto.NutritionGoalRequest;
import com.fitness.app.nutrition.dto.NutritionGoalResponse;
import com.fitness.app.nutrition.service.NutritionGoalService;
import com.fitness.app.nutrition.service.NutritionSummaryService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Centraliza la consulta del resumen nutricional y la meta del miembro.
 * La validación de permisos y alcance se resuelve en los servicios.
 */
@RestController
@RequestMapping("/api/v1/members/{memberId}")
@RequiredArgsConstructor
public class MemberNutritionController
{
    private static final LocalDate NO_LOWER_BOUND = LocalDate.of(1, 1, 1);
    private static final LocalDate NO_UPPER_BOUND = LocalDate.of(9999, 12, 31);

    private final NutritionSummaryService nutritionSummaryService;
    private final NutritionGoalService    nutritionGoalService;

    /** Devuelve el resumen del día o la tendencia por rango de fechas. */
    @GetMapping("/nutrition-summary")
    public Object summary(@PathVariable Long memberId,
                          @RequestParam(required = false) LocalDate date,
                          @RequestParam(required = false) LocalDate from,
                          @RequestParam(required = false) LocalDate to,
                          @AuthenticationPrincipal AuthenticatedUser principal)
    {
        if (from != null || to != null)
        {
            return nutritionSummaryService.trend(memberId,
                    from == null ? NO_LOWER_BOUND : from,
                    to == null ? NO_UPPER_BOUND : to,
                    principal);
        }

        return nutritionSummaryService.daily(memberId, date == null ? LocalDate.now() : date, principal);
    }

    /** Consulta la meta activa del miembro actual o del socio asignado. */
    @GetMapping("/nutrition-goal")
    public NutritionGoalResponse currentGoal(@PathVariable Long memberId,
                                             @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return nutritionGoalService.findCurrent(memberId, principal);
    }

    /** Crea o reemplaza la meta nutricional vigente para el miembro. */
    @PutMapping("/nutrition-goal")
    public NutritionGoalResponse upsertGoal(@PathVariable Long memberId,
                                            @Valid @RequestBody NutritionGoalRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return nutritionGoalService.upsert(memberId, request, principal);
    }
}