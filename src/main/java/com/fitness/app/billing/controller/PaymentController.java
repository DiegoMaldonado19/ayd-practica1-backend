package com.fitness.app.billing.controller;

import com.fitness.app.billing.dto.*;
import com.fitness.app.billing.model.PaymentMethod;
import com.fitness.app.billing.model.PaymentStatus;
import com.fitness.app.billing.service.PaymentService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/payments")
    public Page<PaymentResponse> list(@RequestParam(name = "member_id", required = false) Long memberId,
                                       @RequestParam(required = false) LocalDate from,
                                       @RequestParam(required = false) LocalDate to,
                                       @RequestParam(required = false) PaymentStatus status,
                                       @RequestParam(name = "payment_method", required = false) PaymentMethod paymentMethod,
                                       @AuthenticationPrincipal AuthenticatedUser principal,
                                       Pageable pageable) {
        PaymentFilter filter = new PaymentFilter(memberId, from, to, status, paymentMethod);
        return paymentService.list(filter, principal, pageable);
    }

    @PostMapping("/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse register(@Valid @RequestBody PaymentRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return paymentService.register(request, principal);
    }

    @GetMapping("/payments/{id}")
    public PaymentResponse findById(@PathVariable Long id,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return paymentService.findById(id, principal);
    }

    @PostMapping("/payments/{id}/confirmations")
    public PaymentResponse confirm(@PathVariable Long id) {
        return paymentService.confirm(id);
    }

    @PostMapping("/payments/{id}/voids")
    public PaymentResponse voidPayment(@PathVariable Long id,
                                        @Valid @RequestBody PaymentVoidRequest request) {
        return paymentService.voidPayment(id, request);
    }

    @GetMapping("/payments/{id}/receipt")
    public ReceiptResponse receipt(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return paymentService.getReceipt(id, principal);
    }
}