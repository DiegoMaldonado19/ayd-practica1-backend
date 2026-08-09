package com.fitness.app.membership;

import com.fitness.app.membership.dto.CreateMembershipPlanRequest;
import com.fitness.app.membership.dto.MembershipPlanResponse;
import com.fitness.app.membership.dto.MembershipPlanStatusRequest;
import com.fitness.app.membership.dto.UpdateMembershipPlanRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
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

/** The /membership-plans routes of §3.3. Which role may call what is decided in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/membership-plans")
@RequiredArgsConstructor
public class MembershipPlanController
{
    private final MembershipPlanService membershipPlanService;

    @GetMapping
    public PagedModel<MembershipPlanResponse> list(@RequestParam(required = false) Boolean active,
                                                   Pageable                        pageable)
    {
        return new PagedModel<>(membershipPlanService.search(active, pageable));
    }

    @PostMapping
    public ResponseEntity<MembershipPlanResponse> create(@Valid @RequestBody CreateMembershipPlanRequest request)
    {
        var plan = membershipPlanService.create(request);

        return ResponseEntity.created(URI.create("/api/v1/membership-plans/" + plan.membershipPlanId())).body(plan);
    }

    @GetMapping("/{membershipPlanId}")
    public MembershipPlanResponse detail(@PathVariable Long membershipPlanId)
    {
        return membershipPlanService.findById(membershipPlanId);
    }

    @PutMapping("/{membershipPlanId}")
    public MembershipPlanResponse update(@PathVariable Long membershipPlanId,
                                         @Valid @RequestBody UpdateMembershipPlanRequest request)
    {
        return membershipPlanService.update(membershipPlanId, request);
    }

    @PatchMapping("/{membershipPlanId}/status")
    public MembershipPlanResponse changeStatus(@PathVariable Long membershipPlanId,
                                               @Valid @RequestBody MembershipPlanStatusRequest request)
    {
        return membershipPlanService.changeStatus(membershipPlanId, request.active());
    }
}
