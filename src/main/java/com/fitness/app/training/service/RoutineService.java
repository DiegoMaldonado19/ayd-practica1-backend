package com.fitness.app.training.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.directory.TrainerService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.model.PlanBenefit;
import com.fitness.app.training.TrainerAssignmentService;
import com.fitness.app.training.TrainerScopeException;
import com.fitness.app.training.dto.RoutineRequest;
import com.fitness.app.training.dto.RoutineResponse;
import com.fitness.app.training.dto.RoutineStatusRequest;
import com.fitness.app.training.model.Routine;
import com.fitness.app.training.model.RoutineExercise;
import com.fitness.app.training.model.RoutineStatus;
import com.fitness.app.training.repository.RoutineExerciseRepository;
import com.fitness.app.training.repository.RoutineRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RoutineService
{
    private final RoutineRepository           routineRepository;
    private final RoutineExerciseRepository   routineExerciseRepository;
    private final ExerciseService             exerciseService;
    private final TrainerService              trainerService;
    private final MemberService               memberService;
    private final MembershipService           membershipService;
    private final TrainerAssignmentService    trainerAssignmentService;

    @Transactional(readOnly = true)
    /**
     * Busca rutinas aplicando scope según el rol del usuario y los filtros proporcionados.
     *
     * @param memberId  filtro por socio (opcional)
     * @param trainerId filtro por entrenador (opcional)
     * @param status estado de la rutina (opcional)
     * @param principal usuario autenticado
     * @param pageable  paginación
     * @return página de RoutineResponse
     */
    public Page<RoutineResponse> search(Long memberId, Long trainerId, RoutineStatus status,
                                        AuthenticatedUser principal, Pageable pageable)
    {
        var scopedMemberId  = memberId;
        var scopedTrainerId = trainerId;

        switch (principal.role())
        {
            case MEMBER ->
            {
                var ownMemberId = memberService.findOwnMemberId(principal);

                if (memberId != null && !memberId.equals(ownMemberId))
                {
                    throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
                }

                scopedMemberId = ownMemberId;
            }
            case TRAINER ->
            {
                var selfTrainerId = trainerService.findTrainerIdByUser(principal);

                if (memberId != null && !trainerAssignmentService.isAssignedTo(selfTrainerId, memberId))
                {
                    throw new TrainerScopeException();
                }

                scopedTrainerId = selfTrainerId;
            }
            default -> { }
        }

        return routineRepository.search(scopedMemberId, scopedTrainerId, status, pageable)
                .map(RoutineResponse::from);
    }

    @Transactional(readOnly = true)
    /**
     * Recupera una rutina por id y valida permisos según el socio asociado.
     *
     * @param routineId id de la rutina
     * @param principal usuario autenticado
     * @return `RoutineResponse` con los datos de la rutina
     */
    public RoutineResponse findById(Long routineId, AuthenticatedUser principal)
    {
        var routine = findOrFail(routineId);

        memberService.findById(routine.getMemberId(), principal);

        return RoutineResponse.from(routine);
    }

    /**
     * Crea una nueva rutina (DRAFT) para un socio asignado y con el beneficio requerido.
     *
     * @param request   datos de la rutina
     * @param principal usuario autenticado (entrenador)
     * @return `RoutineResponse` creado
     */
    public RoutineResponse create(RoutineRequest request, AuthenticatedUser principal)
    {
        var trainerId = trainerService.findTrainerIdByUser(principal);

        if (!trainerAssignmentService.isAssignedTo(trainerId, request.memberId()))
        {
            throw new TrainerScopeException();
        }

        if (!membershipService.hasBenefit(request.memberId(), PlanBenefit.PERSONAL_TRAINER))
        {
            throw new BusinessException(ErrorCode.PLAN_BENEFIT_NOT_INCLUDED);
        }

        var routine = new Routine();

        routine.setMemberId(request.memberId());
        routine.setTrainerId(trainerId);
        routine.setName(request.name());
        routine.setGoalSummary(request.goalSummary());
        routine.setStatus(RoutineStatus.DRAFT);
        routine.setStartDate(LocalDate.now());
        routine.setEndDate(request.endDate());
        routine.setCreatedAt(Instant.now());
        routine.getExercises().addAll(toExercises(routine, request));

        return RoutineResponse.from(routineRepository.save(routine));
    }

    /**
     * Reemplaza la rutina y sus ejercicios; mantiene miembro y entrenador.
     *
     * @param routineId id de la rutina a reemplazar
     * @param request   nueva definición de la rutina
     * @param principal usuario autenticado (entrenador)
     * @return `RoutineResponse` actualizado
     */
    public RoutineResponse update(Long routineId, RoutineRequest request, AuthenticatedUser principal)
    {
        var routine = findOrFail(routineId);

        assertAuthor(routine, principal);

        if (!routine.getMemberId().equals(request.memberId()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "La rutina pertenece a otro socio.");
        }

        var exercises = toExercises(routine, request);

        routineExerciseRepository.deleteByRoutine_RoutineId(routineId);
        routineExerciseRepository.flush();

        routine.setName(request.name());
        routine.setGoalSummary(request.goalSummary());
        routine.setEndDate(request.endDate());
        routine.getExercises().clear();
        routine.getExercises().addAll(exercises);
        routine.setUpdatedAt(Instant.now());

        return RoutineResponse.from(routine);
    }

    /**
     * Cambia el estado de una rutina (PUBLISHED o ARCHIVED). Validaciones de transición incluidas.
     *
     * @param routineId id de la rutina
     * @param request   petición con el nuevo estado
     * @param principal usuario autenticado (entrenador)
     * @return `RoutineResponse` con el estado actualizado
     */
    public RoutineResponse changeStatus(Long routineId, RoutineStatusRequest request, AuthenticatedUser principal)
    {
        var routine = findOrFail(routineId);

        assertAuthor(routine, principal);

        switch (request.status())
        {
            case PUBLISHED -> publish(routine);
            case ARCHIVED  -> routine.setStatus(RoutineStatus.ARCHIVED);
            case DRAFT     -> throw new BusinessException(ErrorCode.INVALID_ROUTINE_STATUS_TRANSITION);
        }

        routine.setUpdatedAt(Instant.now());

        return RoutineResponse.from(routine);
    }

    /**
     * Publica la rutina: archiva la publicada vigente (si existe) y marca ésta como PUBLISHED.
     *
     * @param routine rutina a publicar
     */
    private void publish(Routine routine)
    {
        if (routine.getStatus() == RoutineStatus.PUBLISHED)
        {
            return;
        }

        routineRepository.findByMemberIdAndStatus(routine.getMemberId(), RoutineStatus.PUBLISHED)
                .filter(current -> !current.getRoutineId().equals(routine.getRoutineId()))
                .ifPresent(current -> current.setStatus(RoutineStatus.ARCHIVED));

        routineRepository.flush();
        routine.setStatus(RoutineStatus.PUBLISHED);
    }

    /**
     * Verifica que el entrenador autenticado sea el autor de la rutina.
     *
     * @param routine   rutina a comprobar
     * @param principal usuario autenticado
     */
    private void assertAuthor(Routine routine, AuthenticatedUser principal)
    {
        var trainerId = trainerService.findTrainerIdByUser(principal);

        if (!routine.getTrainerId().equals(trainerId))
        {
            throw new TrainerScopeException();
        }
    }

    /**
     * Convierte el DTO de ejercicios a entidades `RoutineExercise` ligadas a la rutina.
     *
     * @param routine rutina padre
     * @param request petición con la lista de ejercicios
     * @return lista de entidades `RoutineExercise`
     */
    private List<RoutineExercise> toExercises(Routine routine, RoutineRequest request)
    {
        return request.exercises().stream()
                .map(item -> {
                    var exercise = new RoutineExercise();

                    exercise.setRoutine(routine);
                    exercise.setExercise(exerciseService.findOrFail(item.exerciseId()));
                    exercise.setWeekday(item.weekday());
                    exercise.setDisplayOrder(item.displayOrder());
                    exercise.setSets(item.sets());
                    exercise.setRepetitions(item.repetitions());
                    exercise.setRestSeconds(item.restSeconds());
                    exercise.setNotes(item.notes());

                    return exercise;
                })
                .toList();
    }

    Routine findOrFail(Long routineId)
    {
        return routineRepository.findById(routineId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROUTINE_NOT_FOUND));
    }
}