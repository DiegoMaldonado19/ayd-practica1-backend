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
    /**
     * Recupera el historial de mediciones de un socio en un rango de fechas.
     *
     * @param memberId id del socio
     * @param from fecha inicial (opcional)
     * @param to fecha final (opcional)
     * @param principal usuario autenticado
     * @return lista de ProgressMeasurementResponse ordenadas ascendentemente
     */
    public List<ProgressMeasurementResponse> findByMember(Long memberId, LocalDate from, LocalDate to,
                                                          AuthenticatedUser principal)
    {
        memberService.findById(memberId, principal);

        return progressMeasurementRepository.findByMemberAndRange(memberId, from, to)
                .stream().map(ProgressMeasurementResponse::from).toList();
    }

    /**
     * Registra una nueva medición para un socio, validando duplicados y alcance.
     *
     * @param memberId  id del socio
     * @param request   datos de la medición
     * @param principal usuario autenticado (entrenador)
     * @return ProgressMeasurementResponse creado
     */
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

    // CORRIGE UNA MEDICION MAL CAPTURADA
    public ProgressMeasurementResponse update(Long measurementId, ProgressMeasurementRequest request,
                                              AuthenticatedUser principal)
    {
        var measurement = findOrFail(measurementId);

        assertOwner(measurement, principal);

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

    public void delete(Long measurementId, AuthenticatedUser principal)
    {
        var measurement = findOrFail(measurementId);

        assertOwner(measurement, principal);
        progressMeasurementRepository.delete(measurement);
    }

    private Long assertAssigned(Long memberId, AuthenticatedUser principal)
    {
        var trainerId = trainerService.findTrainerIdByUser(principal);

        if (!trainerAssignmentService.isAssignedTo(trainerId, memberId))
        {
            throw new TrainerScopeException();
        }

        return trainerId;
    }

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