package com.fitness.app.classes;

import com.fitness.app.classes.dto.WaitlistEntryResponse;
import com.fitness.app.iam.dto.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The member's own waiting queue. GET /class-sessions/{id}/waitlist-entries is staff
 * only, so this is what lets a member reach the waitlistEntryId that
 * POST /waitlist-entries/{id}/confirmations -a MEMBER-only route- requires.
 *
 * Hangs from /members for the same reason MemberEnrollmentController does: the route
 * reads as the member's, but the rows are waitlist_entry's and belong to classes.
 */
@RestController
@RequestMapping("/api/v1/members/{memberId}/waitlist-entries")
@RequiredArgsConstructor
public class MemberWaitlistController
{
    private final WaitlistService waitlistService;

    @GetMapping
    public List<WaitlistEntryResponse> pending(@PathVariable Long memberId,
                                               @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return waitlistService.findByMember(memberId, principal);
    }
}
