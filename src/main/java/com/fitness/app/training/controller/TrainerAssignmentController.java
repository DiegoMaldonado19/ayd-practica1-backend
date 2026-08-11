package com.fitness.app.training.controller;

import com.fitness.app.directory.MemberService;
import com.fitness.app.directory.TrainerService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.TrainerAssignmentService;
import com.fitness.app.training.dto.AssignTrainerRequest;
import com.fitness.app.training.dto.CloseAssignmentRequest;
import com.fitness.app.training.dto.TrainerAssignmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * "El administrador es el único usuario autorizado para asignar formalmente un
 * entrenador personal a un socio" (Enunciado). The member and the trainer both travel
 * in the body here because the route is /trainer-assignments, not /members/{id}/trainer.
 *
 * The same composition as MemberTrainerController: directory validates the member file
 * and the destination profile, training owns the rows. The reassignment of §3.7 closes
 * the previous stretch instead of refusing it.
 */
@RestController
@RequestMapping("/api/v1/trainer-assignments")
@RequiredArgsConstructor
public class TrainerAssignmentController
{
    private final TrainerAssignmentService trainerAssignmentService;
    private final TrainerService           trainerService;
    private final MemberService            memberService;

    @GetMapping
    public PagedModel<TrainerAssignmentResponse> list(
            @RequestParam(name = "member_id", required = false) Long memberId,
            @RequestParam(name = "trainer_id", required = false) Long trainerId,
            @RequestParam(required = false) Boolean active,
            @AuthenticationPrincipal AuthenticatedUser principal,
            Pageable pageable)
    {
        return new PagedModel<>(trainerAssignmentService.search(memberId, trainerId, active, principal, pageable));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainerAssignmentResponse assign(@Valid @RequestBody AssignTrainerRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser principal)
    {
        memberService.findById(request.memberId(), principal);

        var trainer = trainerService.findAssignable(request.trainerId());

        return trainerAssignmentService.reassign(request.memberId(),
                                                 trainer.trainerId(),
                                                 trainer.maxMemberLoad(),
                                                 trainer.person().fullName(),
                                                 principal.appUserId());
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void close(@PathVariable Long assignmentId, @Valid @RequestBody CloseAssignmentRequest request)
    {
        trainerAssignmentService.closeAssignment(assignmentId, request.endReason());
    }
}