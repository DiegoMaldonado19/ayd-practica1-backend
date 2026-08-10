package com.fitness.app.directory;

import com.fitness.app.directory.dto.TrainerResponse;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.TrainerAssignmentService;
import com.fitness.app.training.dto.TrainerAssignmentRequest;
import com.fitness.app.training.dto.TrainerAssignmentResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Entrenador asignado al socio" (§3.2). The route hangs from /members, but the answer
 * is half training's -which stretch is open- and half directory's -who the trainer is-,
 * so it is composed here: directory uses training, never the other way round
 * (02-Modulos §3).
 *
 * Apart from MemberController for the same reason MemberMembershipController is: the
 * handler lives next to the module it may depend on.
 */
@RestController
@RequestMapping("/api/v1/members/{memberId}/trainer")
@RequiredArgsConstructor
public class MemberTrainerController
{
    private final MemberService            memberService;
    private final TrainerService           trainerService;
    private final TrainerAssignmentService trainerAssignmentService;

    @GetMapping
    public TrainerResponse detail(@PathVariable Long memberId)
    {
        return trainerService.findById(trainerAssignmentService.currentTrainerOf(memberId));
    }

    /**
     * Opens the relationship the other two routes read. Same composition as the GET:
     * directory validates the trainer profile and hands training the cap and the name
     * it needs, because training never reads back.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainerAssignmentResponse assign(@PathVariable Long memberId,
                                            @Valid @RequestBody TrainerAssignmentRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser principal)
    {
        // Both ends before writing: without this an unknown member reaches the
        // fk_assign_member violation and answers 500 instead of MEMBER_NOT_FOUND.
        memberService.findById(memberId, principal);

        var trainer = trainerService.findAssignable(request.trainerId());

        return trainerAssignmentService.assign(memberId,
                                               trainer.trainerId(),
                                               trainer.maxMemberLoad(),
                                               trainer.person().fullName(),
                                               principal.appUserId());
    }
}
