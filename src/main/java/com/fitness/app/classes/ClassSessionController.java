package com.fitness.app.classes;

import com.fitness.app.classes.dto.AttendanceRequest;
import com.fitness.app.classes.dto.ClassEnrollmentResponse;
import com.fitness.app.classes.dto.ClassRatingRequest;
import com.fitness.app.classes.dto.ClassRatingResponse;
import com.fitness.app.classes.dto.ClassSessionResponse;
import com.fitness.app.classes.dto.EnrollmentRequest;
import com.fitness.app.classes.dto.SessionCancellationRequest;
import com.fitness.app.classes.dto.SessionRescheduleRequest;
import com.fitness.app.classes.dto.SessionStatusRequest;
import com.fitness.app.classes.dto.WaitlistEntryResponse;
import com.fitness.app.classes.dto.WaitlistRequest;
import com.fitness.app.classes.model.Discipline;
import com.fitness.app.iam.dto.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * The /class-sessions routes of §3.6. Session-level routes (billboard, reschedule,
 * status, cancellations) go through ClassSessionService; the sub-resources
 * (enrollments, attendances, waitlist-entries, ratings) hang off the same path but
 * belong to their own service, exactly like MemberMembershipController does for
 * /members/{id}/memberships.
 */
@RestController
@RequestMapping("/api/v1/class-sessions")
@RequiredArgsConstructor
public class ClassSessionController
{
    private final ClassSessionService classSessionService;
    private final EnrollmentService   enrollmentService;
    private final WaitlistService     waitlistService;
    private final ClassRatingService  classRatingService;

    @GetMapping
    public PagedModel<ClassSessionResponse> list(@RequestParam(required = false) LocalDate from,
                                                 @RequestParam(required = false) LocalDate to,
                                                 @RequestParam(required = false) Discipline discipline,
                                                 @RequestParam(name = "trainer_id", required = false) Long trainerId,
                                                 @RequestParam(name = "group_class_id", required = false) Long groupClassId,
                                                 @RequestParam(name = "has_seats", required = false) Boolean hasSeats,
                                                 Pageable pageable)
    {
        return new PagedModel<>(classSessionService.search(from, to, discipline, trainerId, groupClassId, hasSeats,
                                                            pageable));
    }

    @GetMapping("/{sessionId}")
    public ClassSessionResponse detail(@PathVariable Long sessionId)
    {
        return classSessionService.findById(sessionId);
    }

    @PutMapping("/{sessionId}")
    public ClassSessionResponse reschedule(@PathVariable Long sessionId,
                                           @Valid @RequestBody SessionRescheduleRequest request)
    {
        return classSessionService.reschedule(sessionId, request);
    }

    @PatchMapping("/{sessionId}/status")
    public ClassSessionResponse changeStatus(@PathVariable Long sessionId,
                                             @Valid @RequestBody SessionStatusRequest request,
                                             @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return classSessionService.changeStatus(sessionId, request, principal);
    }

    @PostMapping("/{sessionId}/cancellations")
    public ClassSessionResponse cancel(@PathVariable Long sessionId,
                                       @Valid @RequestBody SessionCancellationRequest request)
    {
        return classSessionService.cancel(sessionId, request);
    }

    @GetMapping("/{sessionId}/enrollments")
    public PagedModel<ClassEnrollmentResponse> enrollments(@PathVariable Long sessionId, Pageable pageable)
    {
        return new PagedModel<>(enrollmentService.roster(sessionId, pageable));
    }

    @PostMapping("/{sessionId}/enrollments")
    public ResponseEntity<ClassEnrollmentResponse> enroll(@PathVariable Long sessionId,
                                                          @Valid @RequestBody EnrollmentRequest request,
                                                          @AuthenticationPrincipal AuthenticatedUser principal)
    {
        var enrollment = enrollmentService.enroll(sessionId, request, principal);

        return ResponseEntity.created(URI.create("/api/v1/enrollments/" + enrollment.classEnrollmentId()))
                .body(enrollment);
    }

    @PutMapping("/{sessionId}/attendances")
    public List<ClassEnrollmentResponse> markAttendance(@PathVariable Long sessionId,
                                                        @Valid @RequestBody AttendanceRequest request,
                                                        @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return enrollmentService.markAttendance(sessionId, request, principal);
    }

    @GetMapping("/{sessionId}/waitlist-entries")
    public PagedModel<WaitlistEntryResponse> waitlistEntries(@PathVariable Long sessionId, Pageable pageable)
    {
        return new PagedModel<>(waitlistService.findBySession(sessionId, pageable));
    }

    @PostMapping("/{sessionId}/waitlist-entries")
    public ResponseEntity<WaitlistEntryResponse> joinWaitlist(@PathVariable Long sessionId,
                                                              @Valid @RequestBody WaitlistRequest request,
                                                              @AuthenticationPrincipal AuthenticatedUser principal)
    {
        var entry = waitlistService.join(sessionId, request, principal);

        return ResponseEntity.created(URI.create("/api/v1/waitlist-entries/" + entry.waitlistEntryId())).body(entry);
    }

    @GetMapping("/{sessionId}/ratings")
    public PagedModel<ClassRatingResponse> ratings(@PathVariable Long sessionId,
                                                    @AuthenticationPrincipal AuthenticatedUser principal,
                                                    Pageable pageable)
    {
        return new PagedModel<>(classRatingService.findBySession(sessionId, principal, pageable));
    }

    @PostMapping("/{sessionId}/ratings")
    @ResponseStatus(HttpStatus.CREATED)
    public ClassRatingResponse rate(@PathVariable Long sessionId,
                                    @Valid @RequestBody ClassRatingRequest request,
                                    @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return classRatingService.rate(sessionId, request, principal);
    }
}
