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
    /**
     * Lista pagos filtrados por socio, fechas, estado y método de pago.
     *
     * @param memberId      filtro por id de socio (opcional)
     * @param from          fecha inicial del pago (opcional)
     * @param to            fecha final del pago (opcional)
     * @param status        estado del pago (opcional)
     * @param paymentMethod método de pago (opcional)
     * @param principal     usuario autenticado que realiza la consulta
     * @param pageable      paginación
     * @return página de `PaymentResponse`
     */
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
    /**
     * Registra un pago nuevo y devuelve su representación.
     *
     * @param request   datos del pago
     * @param principal usuario autenticado que registra el pago
     * @return `PaymentResponse` creado
     */
    public PaymentResponse register(@Valid @RequestBody PaymentRequest request,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return paymentService.register(request, principal);
    }

    @GetMapping("/payments/{id}")
    /**
     * Recupera un pago por id, validando permiso de visualización.
     *
     * @param id        id del pago
     * @param principal usuario autenticado
     * @return `PaymentResponse` del pago
     */
    public PaymentResponse findById(@PathVariable Long id,
                                     @AuthenticationPrincipal AuthenticatedUser principal) {
        return paymentService.findById(id, principal);
    }

    @PostMapping("/payments/{id}/confirmations")
    /**
     * Confirma un pago pendiente y genera su recibo.
     *
     * @param id id del pago a confirmar
     * @return `PaymentResponse` actualizado
     */
    public PaymentResponse confirm(@PathVariable Long id) {
        return paymentService.confirm(id);
    }

    @PostMapping("/payments/{id}/voids")
    /**
     * Anula un pago existente con la razón indicada.
     *
     * @param id      id del pago
     * @param request detalles de la anulación
     * @return `PaymentResponse` actualizado
     */
    public PaymentResponse voidPayment(@PathVariable Long id,
                                        @Valid @RequestBody PaymentVoidRequest request) {
        return paymentService.voidPayment(id, request);
    }

    @GetMapping("/payments/{id}/receipt")
    /**
     * Obtiene el recibo de un pago confirmado, si está disponible.
     *
     * @param id        id del pago
     * @param principal usuario autenticado
     * @return `ReceiptResponse` del recibo
     */
    public ReceiptResponse receipt(@PathVariable Long id,
                                    @AuthenticationPrincipal AuthenticatedUser principal) {
        return paymentService.getReceipt(id, principal);
    }
}