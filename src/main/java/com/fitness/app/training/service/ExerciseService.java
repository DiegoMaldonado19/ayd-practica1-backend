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

/**
 * The exercise catalog: "POST /exercises: crea un ejercicio" and its maintenance (§3.7).
 * The baja is logical - DELETE deactivates, because routines reference exercises with
 * ON DELETE RESTRICT and the history must stay readable.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ExerciseService
{
    private final ExerciseRepository exerciseRepository;

    @Transactional(readOnly = true)
    public Page<ExerciseResponse> search(MuscleGroup muscleGroup, String search, Boolean active, Pageable pageable)
    {
        return exerciseRepository.search(muscleGroup, active, search == null ? "" : search, pageable)
                .map(ExerciseResponse::from);
    }

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
        // The DDL defaults active, but the entity maps it, so a null would break NOT NULL.
        exercise.setActive(true);

        return ExerciseResponse.from(exerciseRepository.save(exercise));
    }

    public ExerciseResponse update(Long exerciseId, ExerciseRequest request)
    {
        var exercise = findOrFail(exerciseId);

        // Changing the code onto one that already exists would trip uq_exercise_code.
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

    /** "DELETE /exercises/{id}: desactiva un ejercicio" (§3.7). */
    public void deactivate(Long exerciseId)
    {
        findOrFail(exerciseId).setActive(false);
    }

    /** The entity, for RoutineService building the routine's rows. */
    Exercise findOrFail(Long exerciseId)
    {
        return exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXERCISE_NOT_FOUND));
    }
}