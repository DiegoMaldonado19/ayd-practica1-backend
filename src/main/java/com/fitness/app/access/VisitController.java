package com.fitness.app.access;

import com.fitness.app.access.dto.CheckInRequest;
import com.fitness.app.access.dto.FacilityVisitResponse;
import com.fitness.app.iam.dto.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;

/**
 * Check-in and check-out endpoints: /api/v1/visits and /api/v1/visits/{id}/check-out
 */
@RestController
@RequestMapping("/api/v1/visits")
@RequiredArgsConstructor
public class VisitController
{
    private final VisitService visitService;

    @PostMapping
    public ResponseEntity<FacilityVisitResponse> checkIn(
            @Valid @RequestBody CheckInRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal)
    {
        var response = visitService.checkIn(request, principal);

        return ResponseEntity
                .created(URI.create("/api/v1/visits/" + response.facilityVisitId()))
                .body(response);
    }

    @PostMapping("/{id}/check-out")
    public ResponseEntity<FacilityVisitResponse> checkOut(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser principal)
    {
        var response = visitService.checkOut(id, principal);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<FacilityVisitResponse>> search(
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Boolean open,
            Pageable pageable)
    {
        var responses = visitService.search(memberId, from, to, open, pageable);

        return ResponseEntity.ok(responses);
    }
}
