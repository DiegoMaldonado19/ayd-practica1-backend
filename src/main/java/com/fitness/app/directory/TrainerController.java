package com.fitness.app.directory;

import com.fitness.app.directory.dto.TrainerResponse;
import com.fitness.app.directory.dto.TrainerSpecialtiesRequest;
import com.fitness.app.directory.dto.UpdateTrainerRequest;
import com.fitness.app.directory.model.Specialty;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.TrainerAssignmentService;
import com.fitness.app.training.dto.MemberTransferRequest;
import com.fitness.app.training.dto.TrainerAssignmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The /trainers routes. Reading is open to every role so a member can choose a
 * trainer; writing belongs to the administrator (SecurityConfig).
 *
 * The has_capacity filter of §3.2 #14 is missing on purpose: capacity is the
 * current caseload against the cap, and the caseload is counted over
 * trainer_assignment, a table of training.
 */
@RestController
@RequestMapping("/api/v1/trainers")
@RequiredArgsConstructor
public class TrainerController
{
    private final TrainerService           trainerService;
    private final TrainerAssignmentService trainerAssignmentService;

    @GetMapping
    public PagedModel<TrainerResponse> list(@RequestParam(required = false) Specialty specialty,
                                            Pageable                                  pageable)
    {
        return new PagedModel<>(trainerService.search(specialty, pageable));
    }

    @GetMapping("/{trainerId}")
    public TrainerResponse detail(@PathVariable Long trainerId)
    {
        return trainerService.findById(trainerId);
    }

    @PutMapping("/{trainerId}")
    public TrainerResponse update(@PathVariable Long trainerId,
                                  @Valid @RequestBody UpdateTrainerRequest request)
    {
        return trainerService.updateProfile(trainerId, request);
    }

    @PutMapping("/{trainerId}/specialties")
    public TrainerResponse replaceSpecialties(@PathVariable Long trainerId,
                                              @Valid @RequestBody TrainerSpecialtiesRequest request)
    {
        return trainerService.replaceSpecialties(trainerId, request);
    }

    /**
     * "La baja de un entrenador transfiere su cartera" (§3.2). The destination profile
     * is validated by directory and its cap and name are handed to training, which owns
     * the assignment rows but never reads this module.
     */
    @PostMapping("/{trainerId}/member-transfers")
    public List<TrainerAssignmentResponse> transferCaseload(@PathVariable Long trainerId,
                                                            @Valid @RequestBody MemberTransferRequest request,
                                                            @AuthenticationPrincipal AuthenticatedUser principal)
    {
        var target = trainerService.findTransferTarget(trainerId, request.toTrainerId());

        return trainerAssignmentService.transferCaseload(trainerId,
                                                         target.trainerId(),
                                                         target.maxMemberLoad(),
                                                         target.person().fullName(),
                                                         principal.appUserId());
    }
}
