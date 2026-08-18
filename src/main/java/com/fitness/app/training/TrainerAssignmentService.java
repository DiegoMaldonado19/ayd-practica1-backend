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

@Service
@RequiredArgsConstructor
@Transactional
public class TrainerAssignmentService
{
    private final TrainerAssignmentRepository trainerAssignmentRepository;
    private final TrainerService trainerService;
    private final MembershipService           membershipService;
    private final NotificationService         notificationService;

    @Transactional(readOnly = true)
    public Long currentTrainerOf(Long memberId)
    {
        return trainerAssignmentRepository.findByMemberIdAndEndDateIsNull(memberId)
                .map(TrainerAssignment::getTrainerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRAINER_ASSIGNMENT_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    /**
     * Busca asignaciones con filtros; si el usuario es TRAINER la búsqueda se limita a su id.
     *
     * @param memberId  filtro por socio (opcional)
     * @param trainerId filtro por entrenador (opcional)
     * @param active    true=open, false=history, null=ambos
     * @param principal usuario autenticado
     * @param pageable  paginación
     * @return página de `TrainerAssignmentResponse`
     */
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


    /**
     * Los socios que el entrenador tiene asignados hoy. directory.MemberService la usa
     * para acotar el listado: "la lista de socios que tiene asignados de forma personal"
     * (Enunciado, §Entrenador).
     *
     * @param trainerId id del entrenador
     * @return ids de los socios con asignación vigente
     */
    @Transactional(readOnly = true)
    public List<Long> assignedMemberIds(Long trainerId)
    {
        return trainerAssignmentRepository.findByTrainerIdAndEndDateIsNull(trainerId).stream()
                .map(TrainerAssignment::getMemberId)
                .toList();
    }

    @Transactional(readOnly = true)
    /**
     * Indica si un trainer está asignado actualmente a un socio.
     *
     * @param trainerId id del entrenador
     * @param memberId  id del socio
     * @return true si existe una asignación vigente
     */
    public boolean isAssignedTo(Long trainerId, Long memberId)
    {
        return trainerAssignmentRepository.existsByTrainerIdAndMemberIdAndEndDateIsNull(trainerId, memberId);
    }

    /**
     * Abre una nueva asignación si el socio tiene el beneficio y el trainer tiene capacidad.
     *
     * @param memberId         id del socio
     * @param trainerId        id del entrenador destino
     * @param maxMemberLoad    capacidad máxima del entrenador
     * @param trainerName      nombre del entrenador (para notificación)
     * @param assignedByUserId id del usuario que realiza la asignación
     * @return `TrainerAssignmentResponse` creado
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
     * Transfiere toda la cartera de `fromTrainerId` a `toTrainerId`, cerrando filas antiguas
     * y abriendo nuevas. Valida capacidad del destino antes de mover.
     *
     * @param fromTrainerId      id del entrenador origen
     * @param toTrainerId        id del entrenador destino
     * @param toTrainerMaxLoad   capacidad máxima del destino
     * @param toTrainerName      nombre del entrenador destino (para notificar)
     * @param assignedByUserId   id del usuario que realiza la transferencia
     * @return lista de `TrainerAssignmentResponse` transferidas
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

        trainerAssignmentRepository.flush();

        var transferred = caseload.stream()
                .map(closed -> open(closed.getMemberId(), toTrainerId, today, now, assignedByUserId))
                .toList();

        trainerAssignmentRepository.saveAll(transferred);
        transferred.forEach(assignment -> notificationService.trainerAssigned(assignment.getMemberId(), toTrainerName));

        return transferred.stream().map(TrainerAssignmentResponse::from).toList();
    }

    /**
     * Crea una instancia `TrainerAssignment` inicializada (helper).
     */
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
     * Cierra una asignación vigente con el motivo indicado.
     *
     * @param assignmentId id de la asignación
     * @param endReason    motivo de cierre
     * @return `TrainerAssignmentResponse` actualizado
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
     * Reasigna un socio a otro entrenador cerrando la anterior asignación y abriendo la nueva.
     *
     * @param memberId         id del socio
     * @param trainerId        id del entrenador destino
     * @param maxMemberLoad    capacidad máxima del entrenador destino
     * @param trainerName      nombre del entrenador destino
     * @param assignedByUserId id del usuario que realiza la reasignación
     * @return `TrainerAssignmentResponse` creado
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
