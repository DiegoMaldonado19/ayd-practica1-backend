package com.fitness.app.training.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.directory.TrainerService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.TrainerAssignmentService;
import com.fitness.app.training.TrainerScopeException;
import com.fitness.app.training.dto.ProgressMeasurementRequest;
import com.fitness.app.training.dto.ProgressMeasurementResponse;
import com.fitness.app.training.model.ProgressMeasurement;
import com.fitness.app.training.repository.ProgressMeasurementRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * "El entrenador registra mediciones periódicas (peso, medidas corporales como
 * cintura/brazo/pierna, y avance respecto al objetivo)... Cada medición debe quedar
 * con fecha, de modo que el socio y el entrenador puedan ver la evolución en el
 * tiempo" (Enunciado).
 *
 * Measurements are follow-up on a relationship that is already open, so the gate is
 * the assignment scope (the trainer's own file), not a fresh benefit check.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ProgressMeasurementService
{
    private final ProgressMeasurementRepository progressMeasurementRepository;
    private final TrainerService                trainerService;
    private final MemberService                 memberService;
    private final TrainerAssignmentService      trainerAssignmentService;

    @Transactional(readOnly = true)
    public List<ProgressMeasurementResponse> findByMember(Long memberId, LocalDate from, LocalDate to,
                                                          AuthenticatedUser principal)
    {
        // The three scopes: ADMIN any member, TRAINER only assigned, MEMBER own file.
        memberService.findById(memberId, principal);

        return progressMeasurementRepository.findByMemberAndRange(memberId, from, to)
                .stream().map(ProgressMeasurementResponse::from).toList();
    }

    public ProgressMeasurementResponse create(Long memberId, ProgressMeasurementRequest request,
                                              AuthenticatedUser principal)
    {
        var trainerId = assertAssigned(memberId, principal);

        if (progressMeasurementRepository.existsByMemberIdAndMeasuredOn(memberId, request.measuredOn()))
        {
            throw new BusinessException(ErrorCode.MEASUREMENT_DUPLICATE_DATE);
        }

        var measurement = new ProgressMeasurement();

        measurement.setMemberId(memberId);
        measurement.setTrainerId(trainerId);
        measurement.setMeasuredOn(request.measuredOn());
        measurement.setWeightKg(request.weightKg());
        measurement.setWaistCm(request.waistCm());
        measurement.setArmCm(request.armCm());
        measurement.setLegCm(request.legCm());
        measurement.setBodyFatPercent(request.bodyFatPercent());
        measurement.setNotes(request.notes());
        measurement.setCreatedAt(Instant.now());

        return ProgressMeasurementResponse.from(progressMeasurementRepository.save(measurement));
    }

    /** "PUT /measurements/{id}: corrige una medición" (§3.7). Only the recording trainer. */
    public ProgressMeasurementResponse update(Long measurementId, ProgressMeasurementRequest request,
                                              AuthenticatedUser principal)
    {
        var measurement = findOrFail(measurementId);

        assertOwner(measurement, principal);

        // The correction may move the measurement to a date that already has one.
        if (progressMeasurementRepository.existsByMemberIdAndMeasuredOnAndProgressMeasurementIdNot(
                measurement.getMemberId(), request.measuredOn(), measurementId))
        {
            throw new BusinessException(ErrorCode.MEASUREMENT_DUPLICATE_DATE);
        }

        measurement.setMeasuredOn(request.measuredOn());
        measurement.setWeightKg(request.weightKg());
        measurement.setWaistCm(request.waistCm());
        measurement.setArmCm(request.armCm());
        measurement.setLegCm(request.legCm());
        measurement.setBodyFatPercent(request.bodyFatPercent());
        measurement.setNotes(request.notes());

        return ProgressMeasurementResponse.from(measurement);
    }

    /** "DELETE /measurements/{id}: elimina una medición mal capturada" (§3.7). */
    public void delete(Long measurementId, AuthenticatedUser principal)
    {
        var measurement = findOrFail(measurementId);

        assertOwner(measurement, principal);
        progressMeasurementRepository.delete(measurement);
    }

    /** The member must be in the caller's caseload; answers with the caller's trainerId. */
    private Long assertAssigned(Long memberId, AuthenticatedUser principal)
    {
        var trainerId = trainerService.findTrainerIdByUser(principal);

        if (!trainerAssignmentService.isAssignedTo(trainerId, memberId))
        {
            throw new TrainerScopeException();
        }

        return trainerId;
    }

    /** A trainer corrects or deletes only the measurements they recorded. */
    private void assertOwner(ProgressMeasurement measurement, AuthenticatedUser principal)
    {
        var trainerId = trainerService.findTrainerIdByUser(principal);

        if (!measurement.getTrainerId().equals(trainerId))
        {
            throw new TrainerScopeException();
        }
    }

    private ProgressMeasurement findOrFail(Long measurementId)
    {
        return progressMeasurementRepository.findById(measurementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEASUREMENT_NOT_FOUND));
    }
}