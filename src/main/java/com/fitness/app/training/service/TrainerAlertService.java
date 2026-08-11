package com.fitness.app.training.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.TrainerService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.training.TrainerAssignmentService;
import com.fitness.app.training.TrainerScopeException;
import com.fitness.app.training.dto.TrainerAlertRequest;
import com.fitness.app.training.dto.TrainerAlertResponse;
import com.fitness.app.training.dto.TrainerAlertStatusRequest;
import com.fitness.app.training.model.TrainerAlert;
import com.fitness.app.training.model.TrainerAlertStatus;
import com.fitness.app.training.repository.TrainerAlertRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * The escalation queue: "el entrenador notifica al administrador si un socio requiere
 * reasignación o atención especial" (Enunciado). The alert row is the notification -
 * the administrator polls the queue with GET /trainer-alerts.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrainerAlertService
{
    private final TrainerAlertRepository   trainerAlertRepository;
    private final TrainerService           trainerService;
    private final TrainerAssignmentService trainerAssignmentService;

    /** "Cola de alertas. Filtro: status" (§3.7). ADMIN sees all; a trainer only their own. */
    @Transactional(readOnly = true)
    public Page<TrainerAlertResponse> search(TrainerAlertStatus status, AuthenticatedUser principal, Pageable pageable)
    {
        var trainerId = principal.role() == UserRole.TRAINER
                ? trainerService.findTrainerIdByUser(principal) : null;

        return trainerAlertRepository.search(trainerId, status, pageable).map(TrainerAlertResponse::from);
    }

    public TrainerAlertResponse create(TrainerAlertRequest request, AuthenticatedUser principal)
    {
        var trainerId = trainerService.findTrainerIdByUser(principal);

        // An escalation is about a member of the caller's caseload, like any other follow-up.
        if (!trainerAssignmentService.isAssignedTo(trainerId, request.memberId()))
        {
            throw new TrainerScopeException();
        }

        var alert = new TrainerAlert();

        alert.setMemberId(request.memberId());
        alert.setTrainerId(trainerId);
        alert.setAlertType(request.alertType());
        alert.setDescription(request.description());
        alert.setStatus(TrainerAlertStatus.PENDING);
        alert.setCreatedAt(Instant.now());

        return TrainerAlertResponse.from(trainerAlertRepository.save(alert));
    }

    /** "PATCH /trainer-alerts/{id}/status: resuelve o descarta la alerta" (§3.7). ADMIN only. */
    public TrainerAlertResponse changeStatus(Long alertId, TrainerAlertStatusRequest request, AuthenticatedUser principal)
    {
        var alert = findOrFail(alertId);

        if (alert.getStatus() != TrainerAlertStatus.PENDING)
        {
            throw new BusinessException(ErrorCode.TRAINER_ALERT_ALREADY_CLOSED);
        }

        switch (request.status())
        {
            case RESOLVED  -> alert.resolve(Instant.now(), principal.appUserId(), request.resolutionNotes());
            case DISMISSED -> alert.dismiss(Instant.now(), principal.appUserId(), request.resolutionNotes());
            case PENDING   -> throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION);
        }

        return TrainerAlertResponse.from(alert);
    }

    private TrainerAlert findOrFail(Long alertId)
    {
        return trainerAlertRepository.findById(alertId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRAINER_ALERT_NOT_FOUND));
    }
}