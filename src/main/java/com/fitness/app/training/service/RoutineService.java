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

/**
 * The member's personal routine: "crea y modifica rutinas personalizadas para los
 * socios que tiene asignados, indicando ejercicios, series, repeticiones y días de
 * entrenamiento sugeridos" (Enunciado).
 *
 * Three scopes share the module's rule: a member only their own routines, a trainer
 * only the members assigned to them, the administrator every routine. Reads with a
 * member in the path delegate the scope to MemberService.findById; the trainer-only
 * writes resolve the caller's trainerId from the token.
 */
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
    public Page<RoutineResponse> search(Long memberId, Long trainerId, RoutineStatus status,
                                        AuthenticatedUser principal, Pageable pageable)
    {
        var scopedMemberId  = memberId;
        var scopedTrainerId = trainerId;

        switch (principal.role())
        {
            // The member's own history: their member_id, whatever filter was passed.
            case MEMBER ->
            {
                var ownMemberId = memberService.findOwnMemberId(principal);

                if (memberId != null && !memberId.equals(ownMemberId))
                {
                    throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
                }

                scopedMemberId = ownMemberId;
            }
            // A trainer may filter by an assigned member, or see the routines they wrote.
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
    public RoutineResponse findById(Long routineId, AuthenticatedUser principal)
    {
        var routine = findOrFail(routineId);

        // MemberService.findById enforces the three scopes on the routine's member:
        // ADMIN any, TRAINER assigned, MEMBER own file.
        memberService.findById(routine.getMemberId(), principal);

        return RoutineResponse.from(routine);
    }

    public RoutineResponse create(RoutineRequest request, AuthenticatedUser principal)
    {
        var trainerId = trainerService.findTrainerIdByUser(principal);

        if (!trainerAssignmentService.isAssignedTo(trainerId, request.memberId()))
        {
            throw new TrainerScopeException();
        }

        // A new training program is the Élite service itself, so the benefit is the
        // gate here too - not just at assignment time (the plan could have changed).
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
     * "PUT /routines/{id}: reemplaza la rutina y sus ejercicios" (§3.7). The routine
     * keeps its member and trainer: it is a correction of the same plan.
     *
     * uq_rtn_ex_order would make the orphan-removal diff collide (inserts before
     * deletes), so the old rows are deleted and flushed first.
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
     * "PATCH /routines/{id}/status: publica o archiva la rutina" (§3.7). DRAFT as a
     * destination makes no sense and is refused; PUBLISHED archives the routine that
     * was in force so uq_routine_published never sees two.
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

    private void publish(Routine routine)
    {
        if (routine.getStatus() == RoutineStatus.PUBLISHED)
        {
            return; // already in force: publishing again is a no-op, not an error.
        }

        // uq_routine_published: at most one PUBLISHED per member. The one in force is
        // archived first and the flush makes the swap atomic against the index.
        routineRepository.findByMemberIdAndStatus(routine.getMemberId(), RoutineStatus.PUBLISHED)
                .filter(current -> !current.getRoutineId().equals(routine.getRoutineId()))
                .ifPresent(current -> current.setStatus(RoutineStatus.ARCHIVED));

        routineRepository.flush();
        routine.setStatus(RoutineStatus.PUBLISHED);
    }

    /** Only the trainer who authored the routine may edit or publish it. */
    private void assertAuthor(Routine routine, AuthenticatedUser principal)
    {
        var trainerId = trainerService.findTrainerIdByUser(principal);

        if (!routine.getTrainerId().equals(trainerId))
        {
            throw new TrainerScopeException();
        }
    }

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