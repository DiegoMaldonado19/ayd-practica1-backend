package com.fitness.app.directory;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.dto.TrainerResponse;
import com.fitness.app.directory.dto.TrainerSpecialtiesRequest;
import com.fitness.app.directory.dto.UpdateTrainerRequest;
import com.fitness.app.directory.model.Specialty;
import com.fitness.app.directory.model.Trainer;
import com.fitness.app.iam.UserService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.TrainerAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * The trainer profile: the load cap, the bio and the specialties.
 *
 * A trainer is never created here. It is born with its employee, because a
 * trainer without a staff file would be a person the gym does not employ, which
 * is why EmployeeService owns the alta.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrainerService
{
    private final TrainerRepository              trainerRepository;
    private final UserService                    userService;
    private final TrainerAssignmentRepository    trainerAssignmentRepository;

    @Transactional(readOnly = true)
    public Page<TrainerResponse> search(Specialty specialty, Boolean hasCapacity, Pageable pageable)
    {
        var trainers = trainerRepository.search(specialty, pageable);

        var caseload = trainerAssignmentRepository.caseloadByTrainer().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));

        return trainers.map(trainer -> {
            long assigned = caseload.getOrDefault(trainer.getTrainerId(), 0L);
            return TrainerResponse.from(trainer, assigned);
        });
    }

    @Transactional(readOnly = true)
    public TrainerResponse findById(Long trainerId)
    {
        var trainer = findOrFail(trainerId);
        var caseload = buildCaseloadMap();
        long assigned = caseload.getOrDefault(trainerId, 0L);
        return TrainerResponse.from(trainer, assigned);
    }

    public TrainerResponse updateProfile(Long trainerId, UpdateTrainerRequest request)
    {
        var trainer = findOrFail(trainerId);

        trainer.setMaxMemberLoad(request.maxMemberLoad());
        trainer.setBio(request.bio());

        var caseload = buildCaseloadMap();
        long assigned = caseload.getOrDefault(trainerId, 0L);
        return TrainerResponse.from(trainer, assigned);
    }

    /**
     * Replaces the whole set, which is what §3.2 #17 asks for. Clearing and
     * refilling the collection is the delete-all plus insert-all that
     * trainer_specialty expects, and it keeps the trainer row untouched.
     */
    public TrainerResponse replaceSpecialties(Long trainerId, TrainerSpecialtiesRequest request)
    {
        var trainer = findOrFail(trainerId);

        trainer.getSpecialties().clear();
        trainer.getSpecialties().addAll(request.specialties());

        var caseload = buildCaseloadMap();
        long assigned = caseload.getOrDefault(trainerId, 0L);
        return TrainerResponse.from(trainer, assigned);
    }

    /**
     * The trainer profile of the signed-in user, for classes' "solo ve sus propias
     * clases" scope check (03-API-REST §3.6/§3.7). TRAINER_NOT_FOUND doubles as the
     * scope failure: a caller with no trainer profile has nothing of its own to see.
     */
    @Transactional(readOnly = true)
    public Long findTrainerIdByUser(AuthenticatedUser principal)
    {
        var personId = userService.findById(principal.appUserId()).personId();

        return trainerRepository.findTrainerIdByPersonId(personId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRAINER_NOT_FOUND));
    }

    /**
     * The trainer that may take members on: a retired one keeps its history but
     * receives nobody. Resolved here because training does not read directory - it
     * gets the cap and the name it needs from this profile.
     */
    @Transactional(readOnly = true)
    public TrainerResponse findAssignable(Long trainerId)
    {
        var trainer = findOrFail(trainerId);

        if (!trainer.isActive())
        {
            throw new BusinessException(ErrorCode.TRANSFER_TARGET_INVALID);
        }

        var caseload = buildCaseloadMap();
        long assigned = caseload.getOrDefault(trainerId, 0L);
        return TrainerResponse.from(trainer, assigned);
    }

    /** The same profile, plus the two checks a caseload transfer adds on top. */
    @Transactional(readOnly = true)
    public TrainerResponse findTransferTarget(Long fromTrainerId, Long toTrainerId)
    {
        findOrFail(fromTrainerId);

        if (fromTrainerId.equals(toTrainerId))
        {
            throw new BusinessException(ErrorCode.TRANSFER_TARGET_INVALID);
        }

        return findAssignable(toTrainerId);
    }

    private Trainer findOrFail(Long trainerId)
    {
        return trainerRepository.findById(trainerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRAINER_NOT_FOUND));
    }

    private Map<Long, Long> buildCaseloadMap()
    {
        return trainerAssignmentRepository.caseloadByTrainer().stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],
                        row -> (Long) row[1]
                ));
    }
}
