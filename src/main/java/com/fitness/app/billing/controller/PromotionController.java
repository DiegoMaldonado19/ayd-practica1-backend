// PromotionController.java
package com.fitness.app.billing.controller;

import com.fitness.app.billing.dto.PromotionRequest;
import com.fitness.app.billing.dto.PromotionResponse;
import com.fitness.app.billing.dto.PromotionStatusRequest;
import com.fitness.app.billing.service.PromotionService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping
    public Page<PromotionResponse> list(@RequestParam(required = false) Boolean active,
                                         Pageable pageable) {
        return promotionService.list(active, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionResponse create(@Valid @RequestBody PromotionRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return promotionService.create(request, principal);
    }

    @GetMapping("/{id}")
    public PromotionResponse findById(@PathVariable Long id) {
        return promotionService.findById(id);
    }

    @PutMapping("/{id}")
    public PromotionResponse update(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        return promotionService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public PromotionResponse updateStatus(@PathVariable Long id,
                                           @Valid @RequestBody PromotionStatusRequest request) {
        return promotionService.updateStatus(id, request.active());
    }
}