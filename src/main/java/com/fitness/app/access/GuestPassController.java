package com.fitness.app.access;

import com.fitness.app.access.dto.GuestPassRequest;
import com.fitness.app.access.dto.GuestPassResponse;
import com.fitness.app.access.model.GuestPassType;
import com.fitness.app.iam.dto.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

/**
 * Guest pass endpoints: /api/v1/guest-passes
 */
@RestController
@RequestMapping("/api/v1/guest-passes")
@RequiredArgsConstructor
public class GuestPassController
{
    private final GuestPassService guestPassService;

    @PostMapping
    public ResponseEntity<GuestPassResponse> create(
            @Valid @RequestBody GuestPassRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal)
    {
        var response = guestPassService.create(request, principal);

        return ResponseEntity
                .created(URI.create("/api/v1/guest-passes/" + response.guestPassId()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<Page<GuestPassResponse>> search(
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) GuestPassType passType,
            Pageable pageable)
    {
        var responses = guestPassService.search(from, to, passType, pageable);

        return ResponseEntity.ok(responses);
    }
}
