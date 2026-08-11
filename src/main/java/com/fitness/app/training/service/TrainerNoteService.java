package com.fitness.app.training.service;

import com.fitness.app.directory.MemberService;
import com.fitness.app.directory.TrainerService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.TrainerAssignmentService;
import com.fitness.app.training.TrainerScopeException;
import com.fitness.app.training.dto.TrainerNoteRequest;
import com.fitness.app.training.dto.TrainerNoteResponse;
import com.fitness.app.training.model.TrainerNote;
import com.fitness.app.training.model.TrainerNoteType;
import com.fitness.app.training.repository.TrainerNoteRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * "POST /members/{id}/notes: deja una observación o recomendación nutricional" (§3.7).
 * Notes are follow-up on an open relationship, so the gate is the assignment scope.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TrainerNoteService
{
    private final TrainerNoteRepository    trainerNoteRepository;
    private final TrainerService           trainerService;
    private final MemberService            memberService;
    private final TrainerAssignmentService trainerAssignmentService;

    @Transactional(readOnly = true)
    public List<TrainerNoteResponse> findByMember(Long memberId, TrainerNoteType noteType, AuthenticatedUser principal)
    {
        // The three scopes: ADMIN any member, TRAINER only assigned, MEMBER own file.
        memberService.findById(memberId, principal);

        return trainerNoteRepository.findByMember(memberId, noteType)
                .stream().map(TrainerNoteResponse::from).toList();
    }

    public TrainerNoteResponse create(Long memberId, TrainerNoteRequest request, AuthenticatedUser principal)
    {
        var trainerId = trainerService.findTrainerIdByUser(principal);

        if (!trainerAssignmentService.isAssignedTo(trainerId, memberId))
        {
            throw new TrainerScopeException();
        }

        var note = new TrainerNote();

        note.setMemberId(memberId);
        note.setTrainerId(trainerId);
        note.setNoteType(request.noteType());
        note.setContent(request.content());
        note.setReferenceDate(request.referenceDate());
        note.setCreatedAt(Instant.now());

        return TrainerNoteResponse.from(trainerNoteRepository.save(note));
    }
}