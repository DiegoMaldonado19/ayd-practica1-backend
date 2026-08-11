package com.fitness.app.training.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.training.dto.ExerciseRequest;
import com.fitness.app.training.dto.ExerciseResponse;
import com.fitness.app.training.model.Exercise;
import com.fitness.app.training.model.MuscleGroup;
import com.fitness.app.training.repository.ExerciseRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ExerciseService
{
    private final ExerciseRepository exerciseRepository;

    @Transactional(readOnly = true)
    /**
     * Busca ejercicios aplicando filtros y devuelve una página de resultados.
     *
     * @param muscleGroup filtro por grupo muscular (opcional)
     * @param search  texto de búsqueda normalizado (no nulo)
     * @param active  filtro por estado activo (opcional)
     * @param pageable paginación
     * @return página de ExerciseResponse
     */
    public Page<ExerciseResponse> search(MuscleGroup muscleGroup, String search, Boolean active, Pageable pageable)
    {
        return exerciseRepository.search(muscleGroup, active, search == null ? "" : search, pageable)
                .map(ExerciseResponse::from);
    }

    /**
     * Crea un nuevo ejercicio validando unicidad del código.
     *
     * @param request datos del ejercicio
     * @return ExerciseResponse creado
     */
    public ExerciseResponse create(ExerciseRequest request)
    {
        if (exerciseRepository.existsByCode(request.code()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ya existe un ejercicio con ese código.");
        }

        var exercise = new Exercise();

        exercise.setCode(request.code());
        exercise.setName(request.name());
        exercise.setMuscleGroup(request.muscleGroup());
        exercise.setDescription(request.description());
        exercise.setVideoUrl(request.videoUrl());
        exercise.setActive(true);

        return ExerciseResponse.from(exerciseRepository.save(exercise));
    }

    /**
     * Actualiza un ejercicio existente.
     *
     * @param exerciseId id del ejercicio
     * @param request nuevos datos
     * @return ExerciseResponse actualizado
     */
    public ExerciseResponse update(Long exerciseId, ExerciseRequest request)
    {
        var exercise = findOrFail(exerciseId);

        if (!request.code().equals(exercise.getCode()) && exerciseRepository.existsByCode(request.code()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ya existe un ejercicio con ese código.");
        }

        exercise.setCode(request.code());
        exercise.setName(request.name());
        exercise.setMuscleGroup(request.muscleGroup());
        exercise.setDescription(request.description());
        exercise.setVideoUrl(request.videoUrl());

        return ExerciseResponse.from(exercise);
    }

    public void deactivate(Long exerciseId)
    {
        findOrFail(exerciseId).setActive(false);
    }

    Exercise findOrFail(Long exerciseId)
    {
        return exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXERCISE_NOT_FOUND));
    }
}