package com.fitness.app.billing.service;

import com.fitness.app.billing.dto.PromotionRequest;
import com.fitness.app.billing.dto.PromotionResponse;
import com.fitness.app.billing.model.DiscountType;
import com.fitness.app.billing.model.Promotion;
import com.fitness.app.billing.repository.PromotionRepository;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.iam.dto.AuthenticatedUser; // VERIFICAR: paquete real de AuthenticatedUser
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PromotionService {

    /** Tope de ck_promotion_pct: un descuento porcentual no pasa del 100 %. */
    private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("100");

    private final PromotionRepository promotionRepository;

    /**
     * Lista promociones, filtrando por activas si se indica.
     *
     * @param active   true para solo activas, false para solo inactivas, null para todas
     * @param pageable paginación
     * @return página de PromotionResponse
     */
    @Transactional(readOnly = true)
    public Page<PromotionResponse> list(Boolean active, Pageable pageable) {
        Page<Promotion> page = (active != null) ? promotionRepository.findByActive(active, pageable) : promotionRepository.findAll(pageable);
        return page.map(PromotionResponse::from);
    }

    /**
     * Recupera una promoción por su id.
     *
     * @param promotionId id de la promoción
     * @return PromotionResponse encontrado
     */
    @Transactional(readOnly = true)
    public PromotionResponse findById(Long promotionId) {
        return PromotionResponse.from(findEntity(promotionId));
    }

    /**
     * Crea una nueva promoción y la activa.
     *
     * @param request   datos de la promoción
     * @param principal usuario autenticado que autoriza la promoción
     * @return PromotionResponse creado
     */
    @Transactional
    public PromotionResponse create(PromotionRequest request, AuthenticatedUser principal) {
        promotionRepository.findByCode(request.code()).ifPresent(p -> {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "El código de promoción ya existe.");
        });

        Promotion promotion = new Promotion();
        applyRequest(promotion, request);
        promotion.setAuthorizedByUserId(principal.appUserId());
        promotion.setAuthorizedAt(Instant.now());
        promotion.setActive(true);

        return PromotionResponse.from(promotionRepository.save(promotion));
    }

    /**
     * Actualiza una promoción existente.
     *
     * @param promotionId id de la promoción
     * @param request     datos actualizados
     * @return PromotionResponse actualizado
     */
    @Transactional
    public PromotionResponse update(Long promotionId, PromotionRequest request) {
        Promotion promotion = findEntity(promotionId);
        applyRequest(promotion, request);
        return PromotionResponse.from(promotion);
    }

    /**
     * Activa o desactiva una promoción.
     *
     * @param promotionId id de la promoción
     * @param active      nuevo estado de la promoción
     * @return PromotionResponse actualizado
     */
    @Transactional
    public PromotionResponse updateStatus(Long promotionId, boolean active) {
        Promotion promotion = findEntity(promotionId);
        promotion.setActive(active);
        return PromotionResponse.from(promotion);
    }

    /**
     * Recupera una promoción activa y válida para uso inmediato.
     *
     * @param promotionId id de la promoción
     * @return promoción válida
     */
    @Transactional(readOnly = true)
    public Promotion getActiveAndValid(Long promotionId) {
        Promotion promotion = findEntity(promotionId);
        LocalDate today = LocalDate.now();
        boolean valid = Boolean.TRUE.equals(promotion.getActive())
                        && !today.isBefore(promotion.getValidFrom())
                    && !today.isAfter(promotion.getValidTo());
        if (!valid) {
            throw new BusinessException(ErrorCode.PROMOTION_NOT_APPLICABLE);
        }
        return promotion;
    }

    private Promotion findEntity(Long promotionId) {
        return promotionRepository.findById(promotionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROMOTION_NOT_FOUND));
    }

    /**
     * Las dos reglas que solo vivían en la base (ck_promotion_dates y ck_promotion_pct).
     * Comprobarlas aquí las devuelve como 400 en lugar de dejarlas escapar como 500;
     * va en applyRequest porque es el único punto por el que pasan create y update.
     */
    private void assertConsistent(PromotionRequest request) {
        if (request.validTo().isBefore(request.validFrom())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La vigencia no puede terminar antes de empezar.");
        }

        if (request.discountType() == DiscountType.PERCENTAGE
                && request.discountValue().compareTo(MAX_PERCENTAGE) > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Un descuento porcentual no puede exceder el 100 %.");
        }
    }

    private void applyRequest(Promotion promotion, PromotionRequest request) {
        assertConsistent(request);

        promotion.setCode(request.code());
        promotion.setName(request.name());
        promotion.setDescription(request.description());
        promotion.setDiscountType(request.discountType());
        promotion.setDiscountValue(request.discountValue());
        promotion.setValidFrom(request.validFrom());
        promotion.setValidTo(request.validTo());
        promotion.setMaxUses(request.maxUses());
        promotion.setMaxUsesPerMember(request.maxUsesPerMember());
    }
}