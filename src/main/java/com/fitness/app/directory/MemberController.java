package com.fitness.app.directory;

import com.fitness.app.directory.dto.MemberRequest;
import com.fitness.app.directory.dto.MemberResponse;
import com.fitness.app.directory.dto.MemberStatusRequest;
import com.fitness.app.directory.model.MemberStatus;
import com.fitness.app.iam.dto.AuthenticatedUser;
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
 * The plan_code and membership_status filters of §3.2 #1 are missing on purpose:
 * they read the membership table, which belongs to a module that does not exist
 * yet.
 */
@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberController
{
    private final MemberService memberService;

    @GetMapping
    public PagedModel<MemberResponse> list(@RequestParam(required = false) MemberStatus status,
                                           @RequestParam(required = false) String       search,
                                           Pageable                                     pageable)
    {
        return new PagedModel<>(memberService.search(status, search, pageable));
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
