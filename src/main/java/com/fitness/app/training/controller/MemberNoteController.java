package com.fitness.app.training.controller;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.training.dto.TrainerNoteRequest;
import com.fitness.app.training.dto.TrainerNoteResponse;
import com.fitness.app.training.model.TrainerNoteType;
import com.fitness.app.training.service.TrainerNoteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** "Observaciones del entrenador. Filtro: note_type" (§3.7). */
@RestController
@RequestMapping("/api/v1/members/{memberId}/notes")
@RequiredArgsConstructor
public class MemberNoteController
{
    private final TrainerNoteService trainerNoteService;

    @GetMapping
    public List<TrainerNoteResponse> history(@PathVariable Long memberId,
                                             @RequestParam(name = "note_type", required = false) TrainerNoteType noteType,
                                             @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return trainerNoteService.findByMember(memberId, noteType, principal);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrainerNoteResponse create(@PathVariable Long memberId,
                                      @Valid @RequestBody TrainerNoteRequest request,
                                      @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return trainerNoteService.create(memberId, request, principal);
    }
}