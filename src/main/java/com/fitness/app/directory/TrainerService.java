package com.fitness.app.directory;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.dto.TrainerResponse;
import com.fitness.app.directory.dto.TrainerSpecialtiesRequest;
import com.fitness.app.directory.dto.UpdateTrainerRequest;
import com.fitness.app.directory.model.Specialty;
import com.fitness.app.directory.model.Trainer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final TrainerRepository trainerRepository;

    @Transactional(readOnly = true)
    public Page<TrainerResponse> search(Specialty specialty, Pageable pageable)
    {
        return trainerRepository.search(specialty, pageable).map(TrainerResponse::from);
    }

    @Transactional(readOnly = true)
    public TrainerResponse findById(Long trainerId)
    {
        return TrainerResponse.from(findOrFail(trainerId));
    }

    public TrainerResponse updateProfile(Long trainerId, UpdateTrainerRequest request)
    {
        var trainer = findOrFail(trainerId);

        trainer.setMaxMemberLoad(request.maxMemberLoad());
        trainer.setBio(request.bio());

        return TrainerResponse.from(trainer);
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

        return TrainerResponse.from(trainer);
    }

    private Trainer findOrFail(Long trainerId)
    {
        return trainerRepository.findById(trainerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TRAINER_NOT_FOUND));
    }
}
