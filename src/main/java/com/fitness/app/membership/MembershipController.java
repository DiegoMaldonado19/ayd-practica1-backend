package com.fitness.app.membership;

import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.membership.dto.CancellationRequest;
import com.fitness.app.membership.dto.FreezeHistoryResponse;
import com.fitness.app.membership.dto.FreezeRequest;
import com.fitness.app.membership.dto.MembershipFreezeResponse;
import com.fitness.app.membership.dto.MembershipRequest;
import com.fitness.app.membership.dto.MembershipResponse;
import com.fitness.app.membership.dto.PlanChangeRequest;
import com.fitness.app.membership.model.MembershipStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * The /memberships routes. Which role may call what is decided in SecurityConfig;
 * whether a member may reach *this* contract is decided in MembershipService, because
 * a path matcher cannot compare the caller with the row.
 *
 * The state changes that carry their own data are POST sub-resources (§1): freezes
 * writes a real row in membership_freeze, and renewals and plan-changes create the
 * next contract, so those three answer 201.
 */
@RestController
@RequestMapping("/api/v1/memberships")
@RequiredArgsConstructor
public class MembershipController
{
    private final MembershipService membershipService;

    /**
     * The query parameters are named by hand because the SNAKE_CASE strategy of
     * application.yml only renames JSON properties: a @RequestParam binds by the Java
     * parameter name, so plan_id would silently arrive null.
     */
    @GetMapping
    public PagedModel<MembershipResponse> list(@RequestParam(required = false)
                                               MembershipStatus status,
                                               @RequestParam(name = "plan_id", required = false)
                                               Long             planId,
                                               @RequestParam(name = "expiring_in_days", required = false)
                                               Integer          expiringInDays,
                                               Pageable         pageable)
    {
        return new PagedModel<>(membershipService.search(status, planId, expiringInDays, pageable));
    }

    @PostMapping
    public ResponseEntity<MembershipResponse> create(@Valid @RequestBody MembershipRequest request,
                                                     @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return created(membershipService.create(request, principal));
    }

    @GetMapping("/{membershipId}")
    public MembershipResponse detail(@PathVariable Long membershipId,
                                     @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return membershipService.findById(membershipId, principal);
    }

    @GetMapping("/{membershipId}/freezes")
    public FreezeHistoryResponse freezes(@PathVariable Long membershipId,
                                         @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return membershipService.findFreezes(membershipId, principal);
    }

    @PostMapping("/{membershipId}/freezes")
    @ResponseStatus(HttpStatus.CREATED)
    public MembershipFreezeResponse freeze(@PathVariable Long membershipId,
                                           @Valid @RequestBody FreezeRequest request,
                                           @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return membershipService.freeze(membershipId, request, principal);
    }

    /** No body: what recomputes the expiry date is today's date, not anything sent in. */
    @PostMapping("/{membershipId}/reactivations")
    public MembershipResponse reactivate(@PathVariable Long membershipId,
                                         @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return membershipService.reactivate(membershipId, principal);
    }

    @PostMapping("/{membershipId}/renewals")
    public ResponseEntity<MembershipResponse> renew(@PathVariable Long membershipId,
                                                    @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return created(membershipService.renew(membershipId, principal));
    }

    @PostMapping("/{membershipId}/plan-changes")
    public ResponseEntity<MembershipResponse> changePlan(@PathVariable Long membershipId,
                                                         @Valid @RequestBody PlanChangeRequest request,
                                                         @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return created(membershipService.changePlan(membershipId, request, principal));
    }

    @PostMapping("/{membershipId}/cancellations")
    public MembershipResponse cancel(@PathVariable Long membershipId,
                                     @Valid @RequestBody CancellationRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser principal)
    {
        return membershipService.cancel(membershipId, request, principal);
    }

    /** The alta, the renewal and the plan change all answer with the new contract. */
    private static ResponseEntity<MembershipResponse> created(MembershipResponse membership)
    {
        return ResponseEntity.created(URI.create("/api/v1/memberships/" + membership.membershipId()))
                .body(membership);
    }
}
