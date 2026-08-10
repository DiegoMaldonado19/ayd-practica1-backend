package com.fitness.app.classes;

import com.fitness.app.iam.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * "DELETE /enrollments/{id}, no POST /cancelEnrollment/{id}" (§1): the cancellation
 * hangs from the root and not from /class-sessions/{id}/enrollments/{eid} to keep the
 * nesting at two levels (§7).
 */
@RestController
@RequestMapping("/api/v1/enrollments")
@RequiredArgsConstructor
public class EnrollmentController
{
    private final EnrollmentService enrollmentService;

    @DeleteMapping("/{enrollmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long enrollmentId,
                       @AuthenticationPrincipal AuthenticatedUser principal)
    {
        enrollmentService.cancel(enrollmentId, principal);
    }
}
