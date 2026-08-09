package com.fitness.app.directory;

import com.fitness.app.directory.dto.MemberRequest;
import com.fitness.app.directory.dto.MemberResponse;
import com.fitness.app.directory.dto.MemberStatusRequest;
import com.fitness.app.directory.model.MemberStatus;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.model.MembershipStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
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
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * The /members routes. Which role may call what is decided in SecurityConfig;
 * whether a member may see this particular file is decided in MemberService,
 * because a path matcher cannot compare the caller with the row.
 *
 * The history of contracts of §3.2 hangs from this path but is served by
 * MemberMembershipController: those rows belong to membership, and the dependency
 * matrix of 02-Modulos §3 only allows that direction.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController
{
    private final MemberService     memberService;
    private final MembershipService membershipService;

    /**
     * plan_code and membership_status are resolved by membership and arrive here as
     * member ids. Asking the other service from MemberService instead would close the
     * loop that MembershipService already opens towards directory.
     *
     * Both are named by hand: the SNAKE_CASE strategy of application.yml renames JSON
     * properties, not query parameters, so plan_code would silently arrive null.
     */
    @GetMapping
    public PagedModel<MemberResponse> list(@RequestParam(required = false)
                                           MemberStatus     status,
                                           @RequestParam(name = "plan_code", required = false)
                                           String           planCode,
                                           @RequestParam(name = "membership_status", required = false)
                                           MembershipStatus membershipStatus,
                                           @RequestParam(required = false)
                                           String           search,
                                           Pageable         pageable)
    {
        var memberIds = membershipService.findMemberIds(planCode, membershipStatus);

        return new PagedModel<>(memberService.search(status, memberIds, search, pageable));
    }

    @PostMapping
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberRequest request)
    {
        var member = memberService.create(request);

        return ResponseEntity.created(URI.create("/api/v1/members/" + member.memberId())).body(member);
    }

    @GetMapping("/{memberId}")
    public MemberResponse detail(@PathVariable Long memberId,
                                 @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return memberService.findById(memberId, principal);
    }

    @PutMapping("/{memberId}")
    public MemberResponse update(@PathVariable Long memberId,
                                 @Valid @RequestBody MemberRequest request,
                                 @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return memberService.update(memberId, request, principal);
    }

    @PatchMapping("/{memberId}/status")
    public MemberResponse changeStatus(@PathVariable Long memberId,
                                       @Valid @RequestBody MemberStatusRequest request)
    {
        return memberService.changeStatus(memberId, request.status());
    }
}
