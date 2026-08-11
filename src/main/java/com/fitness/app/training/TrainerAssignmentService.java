package com.fitness.app.training;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.TrainerService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.model.PlanBenefit;
import com.fitness.app.notification.NotificationService;
import com.fitness.app.training.dto.TrainerAssignmentResponse;
import com.fitness.app.training.model.AssignmentEndReason;
import com.fitness.app.training.model.TrainerAssignment;
import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The member-trainer relationship: the slice of training the two remaining Directory
 * endpoints of §3.2 need, and nothing else. Routines, exercises, measurements and
 * alerts arrive with their own module.
 *
 * Nothing here reads directory. The dependency matrix of 02-Modulos §3 points
 * directory to training to break their cycle, so the destination trainer's cap and
 * name travel in as parameters instead of being looked up: the caller already holds
 * the profile it validated.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrainerAssignmentService
{
    private final TrainerAssignmentRepository trainerAssignmentRepository;
    private final TrainerService trainerService;
    private final MembershipService           membershipService;
    private final NotificationService         notificationService;

    /** The trainer in force for a member, for GET /members/{id}/trainer. */
    @Transactional(readOnly = true)
    public Long currentTrainerOf(Long memberId)
    {
        return trainerAssignmentRepository.findByMemberIdAndEndDateIsNull(memberId)
                .map(TrainerAssignment::getTrainerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRAINER_ASSIGNMENT_NOT_FOUND));
    }

    /**
     * "Listado y cartera de un entrenador. Filtros: member_id, trainer_id, active"
     * (§3.7). "El entrenador solo puede filtrar por sí mismo": whatever filter a TRAINER
     * passes, the search is scoped to their own trainerId; asking for another's is
     * FORBIDDEN_RESOURCE. ADMIN sees the whole book.
     */
    @Transactional(readOnly = true)
    public Page<TrainerAssignmentResponse> search(Long memberId, Long trainerId, Boolean active,
                                                  AuthenticatedUser principal, Pageable pageable)
    {
        var scopedTrainerId = trainerId;

        if (principal.role() == UserRole.TRAINER)
        {
            var selfTrainerId = trainerService.findTrainerIdByUser(principal);

            if (trainerId != null && !trainerId.equals(selfTrainerId))
            {
                throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
            }

            scopedTrainerId = selfTrainerId;
        }

        return trainerAssignmentRepository.search(memberId, scopedTrainerId, active, pageable)
                .map(TrainerAssignmentResponse::from);
    }


    /** "El entrenador solo ve a los suyos" (§3.2): the fact directory asks about. */
    @Transactional(readOnly = true)
    public boolean isAssignedTo(Long trainerId, Long memberId)
    {
        return trainerAssignmentRepository.existsByTrainerIdAndMemberIdAndEndDateIsNull(trainerId, memberId);
    }

    /**
     * Opens the relationship. Nothing else creates a trainer_assignment row, so
     * without this the scope of §3.2 would leave every trainer with no file to read
     * and there would never be a caseload to transfer.
     *
     * uq_assign_current is the rule being guarded: one open row per member. The cap
     * is the destination's, and it travels in because training does not read
     * directory.
     *
     * The plan is checked first because it is the eligibility gate: "el sistema debe
     * validar, en cada intento de inscripción a una clase o de solicitud de entrenador,
     * si el plan actual del socio incluye ese beneficio" (Enunciado). Only Élite
     * carries PERSONAL_TRAINER, and PLAN_BENEFIT_NOT_INCLUDED already suggests
     * UPGRADE_PLAN, which is the upgrade the same paragraph asks to offer.
     */
    public TrainerAssignmentResponse assign(Long memberId, Long trainerId, short maxMemberLoad,
                                            String trainerName, Long assignedByUserId)
    {
        if (!membershipService.hasBenefit(memberId, PlanBenefit.PERSONAL_TRAINER))
        {
            throw new BusinessException(ErrorCode.PLAN_BENEFIT_NOT_INCLUDED);
        }

        if (trainerAssignmentRepository.findByMemberIdAndEndDateIsNull(memberId).isPresent())
        {
            throw new BusinessException(ErrorCode.TRAINER_ALREADY_ASSIGNED);
        }

        if (trainerAssignmentRepository.countByTrainerIdAndEndDateIsNull(trainerId) >= maxMemberLoad)
        {
            throw new BusinessException(ErrorCode.TRAINER_CAPACITY_EXCEEDED);
        }

        var assignment = open(memberId, trainerId, LocalDate.now(), Instant.now(), assignedByUserId);

        trainerAssignmentRepository.save(assignment);
        notificationService.trainerAssigned(memberId, trainerName);

        return TrainerAssignmentResponse.from(assignment);
    }

    /**
     * "Transferir la cartera de un entrenador a otro sin perder el historial" (§3.2).
     * Each member's open row is closed with TRAINER_LEFT and a fresh one is opened on
     * the destination, so the previous stretch stays readable.
     *
     * The destination is validated as a whole before anything moves: a partial
     * transfer would leave the caseload split between two trainers.
     */
    public List<TrainerAssignmentResponse> transferCaseload(Long   fromTrainerId,
                                                            Long   toTrainerId,
                                                            short  toTrainerMaxLoad,
                                                            String toTrainerName,
                                                            Long   assignedByUserId)
    {
        var caseload = trainerAssignmentRepository.findByTrainerIdAndEndDateIsNull(fromTrainerId);

        if (caseload.isEmpty())
        {
            return List.of();
        }

        if (trainerAssignmentRepository.countByTrainerIdAndEndDateIsNull(toTrainerId) + caseload.size() > toTrainerMaxLoad)
        {
            throw new BusinessException(ErrorCode.TRAINER_CAPACITY_EXCEEDED);
        }

        var today = LocalDate.now();
        var now   = Instant.now();

        caseload.forEach(assignment -> assignment.close(today, AssignmentEndReason.TRAINER_LEFT));

        // uq_assign_current allows one open row per member, and Hibernate flushes
        // inserts before updates: without this the new rows would hit the index while
        // the old ones are still open.
        trainerAssignmentRepository.flush();

        var transferred = caseload.stream()
                .map(closed -> open(closed.getMemberId(), toTrainerId, today, now, assignedByUserId))
                .toList();

        trainerAssignmentRepository.saveAll(transferred);
        transferred.forEach(assignment -> notificationService.trainerAssigned(assignment.getMemberId(), toTrainerName));

        return transferred.stream().map(TrainerAssignmentResponse::from).toList();
    }

    private static TrainerAssignment open(Long memberId, Long trainerId, LocalDate today, Instant now, Long assignedByUserId)
    {
        var assignment = new TrainerAssignment();

        assignment.setMemberId(memberId);
        assignment.setTrainerId(trainerId);
        assignment.setStartDate(today);
        assignment.setAssignedByUserId(assignedByUserId);
        assignment.setCreatedAt(now);

        return assignment;
    }

    /**
     * "DELETE /trainer-assignments/{id}: cierra la asignación vigente con su motivo"
     * (§3.7). A closed row is closed: closing twice is INVALID_STATE_TRANSITION.
     */
    public TrainerAssignmentResponse closeAssignment(Long assignmentId, AssignmentEndReason endReason)
    {
        var assignment = trainerAssignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRAINER_ASSIGNMENT_NOT_FOUND));

        if (assignment.getEndDate() != null)
        {
            throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        assignment.close(LocalDate.now(), endReason);

        return TrainerAssignmentResponse.from(assignment);
    }

    /**
     * "POST /trainer-assignments: asigna entrenador a un socio... Cierra la asignación
     * anterior si existía" (§3.7). Unlike assign (the §3.2 endpoint, which refuses a
     * second trainer), this is the reassignment path: the previous stretch is closed
     * with REASSIGNMENT and a fresh one is opened, so the history is kept - the same
     * "sin perder el historial" rule as transferCaseload.
     *
     * uq_assign_current requires the closing update to reach the database before the
     * insert, hence the flush, exactly as in transferCaseload. Reassigning the same
     * trainer is a no-op and refused, not a silent re-open.
     */
    public TrainerAssignmentResponse reassign(Long memberId, Long trainerId, short maxMemberLoad,
                                              String trainerName, Long assignedByUserId)
    {
        if (!membershipService.hasBenefit(memberId, PlanBenefit.PERSONAL_TRAINER))
        {
            throw new BusinessException(ErrorCode.PLAN_BENEFIT_NOT_INCLUDED);
        }

        var current = trainerAssignmentRepository.findByMemberIdAndEndDateIsNull(memberId);

        if (current.isPresent() && current.get().getTrainerId().equals(trainerId))
        {
            throw new BusinessException(ErrorCode.TRAINER_ALREADY_ASSIGNED);
        }

        if (trainerAssignmentRepository.countByTrainerIdAndEndDateIsNull(trainerId) >= maxMemberLoad)
        {
            throw new BusinessException(ErrorCode.TRAINER_CAPACITY_EXCEEDED);
        }

        current.ifPresent(assignment -> assignment.close(LocalDate.now(), AssignmentEndReason.REASSIGNMENT));
        trainerAssignmentRepository.flush();

        var assignment = open(memberId, trainerId, LocalDate.now(), Instant.now(), assignedByUserId);

        trainerAssignmentRepository.save(assignment);
        notificationService.trainerAssigned(memberId, trainerName);

        return TrainerAssignmentResponse.from(assignment);
    }


}
