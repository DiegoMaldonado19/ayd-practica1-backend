package com.fitness.app.training;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.model.PlanBenefit;
import com.fitness.app.notification.NotificationService;
import com.fitness.app.training.dto.TrainerAssignmentResponse;
import com.fitness.app.training.model.AssignmentEndReason;
import com.fitness.app.training.model.TrainerAssignment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /** "El entrenador solo ve a los suyos" (§3.2): the fact directory asks about. */
    @Transactional(readOnly = true)
    public boolean isAssignedTo(Long trainerId, Long memberId)
    {
        return trainerAssignmentRepository.existsByTrainerIdAndMemberIdAndEndDateIsNull(trainerId, memberId);
    }

    /**
     * Caseload by trainer: used by directory to populate the current load for each trainer
     * and to filter by has_capacity.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> caseloadByTrainer()
    {
        return trainerAssignmentRepository.caseloadByTrainer().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],  // trainerId
                        row -> (Long) row[1]   // count
                ));
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
}
