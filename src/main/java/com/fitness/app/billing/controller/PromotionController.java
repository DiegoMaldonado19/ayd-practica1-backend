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
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/promotions")
@RequiredArgsConstructor
public class PromotionController {

    private final PromotionService promotionService;

    @GetMapping
    /**
     * Lista las promociones disponibles y permite filtrar por activas.
     *
     * @param active   true para solo activas, false para solo inactivas, null para
     *                 todas
     * @param pageable paginación
     * @return página de `PromotionResponse`
     */
    public Page<PromotionResponse> list(@RequestParam(required = false) Boolean active,
            Pageable pageable) {
        return promotionService.list(active, pageable);
    }

    @PostMapping
    /**
     * Crea una nueva promoción.
     *
     * @param request   datos de la promoción
     * @param principal usuario autenticado que autoriza la promoción
     * @return `PromotionResponse` creado, con su Location
     */
    public ResponseEntity<PromotionResponse> create(@Valid @RequestBody PromotionRequest request,
            @AuthenticationPrincipal AuthenticatedUser principal) {
        PromotionResponse promotion = promotionService.create(request, principal);

        return ResponseEntity.created(URI.create("/api/v1/promotions/" + promotion.promotionId()))
                .body(promotion);
    }

    @GetMapping("/{id}")
    /**
     * Recupera una promoción por su id.
     *
     * @param id id de la promoción
     * @return `PromotionResponse` encontrado
     */
    public PromotionResponse findById(@PathVariable Long id) {
        return promotionService.findById(id);
    }

    @PutMapping("/{id}")
    /**
     * Actualiza los datos de una promoción existente.
     *
     * @param id      id de la promoción
     * @param request datos actualizados
     * @return `PromotionResponse` actualizado
     */
    public PromotionResponse update(@PathVariable Long id, @Valid @RequestBody PromotionRequest request) {
        return promotionService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    /**
     * Activa o desactiva una promoción existente.
     *
     * @param id      id de la promoción
     * @param request nuevo estado activo
     * @return `PromotionResponse` actualizado
     */
    public PromotionResponse updateStatus(@PathVariable Long id,
            @Valid @RequestBody PromotionStatusRequest request) {
        return promotionService.updateStatus(id, request.active());
    }
}