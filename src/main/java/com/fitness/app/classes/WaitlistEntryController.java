package com.fitness.app.classes;

import com.fitness.app.classes.dto.ClassEnrollmentResponse;
import com.fitness.app.iam.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Abandonar la cola y confirmar el cupo liberado (§3.6). The confirmation is a POST
 * sub-resource because it creates a real row - the enrolment - so it answers 201 with
 * the enrolment it produced, the same shape MembershipController.freeze uses.
 */
@RestController
@RequestMapping("/api/v1/waitlist-entries")
@RequiredArgsConstructor
public class WaitlistEntryController
{
    private final WaitlistService waitlistService;

    @DeleteMapping("/{waitlistEntryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@PathVariable Long waitlistEntryId,
                      @AuthenticationPrincipal AuthenticatedUser principal)
    {
        waitlistService.leave(waitlistEntryId, principal);
    }

    @PostMapping("/{waitlistEntryId}/confirmations")
    public ResponseEntity<ClassEnrollmentResponse> confirm(@PathVariable Long waitlistEntryId,
                                                           @AuthenticationPrincipal AuthenticatedUser principal)
    {
        var enrollment = waitlistService.confirm(waitlistEntryId, principal);

        return ResponseEntity.created(URI.create("/api/v1/enrollments/" + enrollment.classEnrollmentId()))
                .body(enrollment);
    }
}
