package com.fitness.app.training.controller;

import com.fitness.app.training.dto.ExerciseRequest;
import com.fitness.app.training.dto.ExerciseResponse;
import com.fitness.app.training.model.MuscleGroup;
import com.fitness.app.training.service.ExerciseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.net.URI;


@RestController
@RequestMapping("/api/v1/exercises")
@RequiredArgsConstructor
public class ExerciseController
{
    private final ExerciseService exerciseService;

    @GetMapping
    /**
     * Devuelve una página de ejercicios filtrada.
     *
     * @param muscleGroup filtro por grupo muscular (opcional)
     * @param search texto de búsqueda sobre nombre o código (opcional)
     * @param active filtro por estado activo/inactivo (opcional)
     * @param pageable datos de paginación
     * @return página de respuestas `ExerciseResponse` que coincide con los filtros
     */
    public PagedModel<ExerciseResponse> list(@RequestParam(name = "muscle_group", required = false) MuscleGroup muscleGroup,
                                             @RequestParam(required = false) String search,
                                             @RequestParam(required = false) Boolean active,
                                             Pageable pageable)
    {
        return new PagedModel<>(exerciseService.search(muscleGroup, search, active, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    /**
     * Crea un nuevo ejercicio en el catálogo.
     *
     * @param request datos del ejercicio a crear
     * @return `ResponseEntity` con código 201 y la representación creada
     */
    public ResponseEntity<ExerciseResponse> create(@Valid @RequestBody ExerciseRequest request)
    {
        var exercise = exerciseService.create(request);

        return ResponseEntity.created(URI.create("/api/v1/exercises/" + exercise.exerciseId())).body(exercise);
    }

    @PutMapping("/{exerciseId}")
    /**
     * Actualiza un ejercicio existente identificado por `exerciseId`.
     *
     * @param exerciseId id del ejercicio a actualizar
     * @param request    nuevos datos del ejercicio
     * @return la representación actualizada del ejercicio
     */
    public ExerciseResponse update(@PathVariable Long exerciseId, @Valid @RequestBody ExerciseRequest request)
    {
        return exerciseService.update(exerciseId, request);
    }

    @DeleteMapping("/{exerciseId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
        /**
         * Desactiva lógicamente un ejercicio (baja lógica).
         *
         * @param exerciseId id del ejercicio a desactivar
         */
        public void deactivate(@PathVariable Long exerciseId)
        {
            exerciseService.deactivate(exerciseId);
        }
}