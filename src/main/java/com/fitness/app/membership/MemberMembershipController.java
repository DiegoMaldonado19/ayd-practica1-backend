package com.fitness.app.membership;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.membership.dto.MembershipResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * "Historial de contratos del socio" (§3.2). The route hangs from /members, but the
 * rows are membership's, and the dependency matrix of 02-Modulos §3 says directory
 * does not use membership - only the other way round. Serving it from MemberController
 * would close that loop into a bean cycle, so the handler lives where the data does.
 */
@RestController
@RequestMapping("/api/v1/members/{memberId}/memberships")
@RequiredArgsConstructor
public class MemberMembershipController
{
    private final MembershipService membershipService;

    @GetMapping
    public PagedModel<MembershipResponse> history(@PathVariable Long memberId,
                                                  @AuthenticationPrincipal AuthenticatedUser principal,
                                                  Pageable pageable)
    {
        return new PagedModel<>(membershipService.findHistory(memberId, principal, pageable));
    }
}
