package com.fitness.app.billing.service;

import com.fitness.app.billing.dto.PromotionRequest;
import com.fitness.app.billing.dto.PromotionResponse;
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

import java.time.Instant;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PromotionService {

    private final PromotionRepository promotionRepository;

    @Transactional(readOnly = true)
    public Page<PromotionResponse> list(Boolean active, Pageable pageable) {
        Page<Promotion> page = (active != null) ? promotionRepository.findByActive(active, pageable) : promotionRepository.findAll(pageable);
        return page.map(PromotionResponse::from);
    }

    @Transactional(readOnly = true)
    public PromotionResponse findById(Long promotionId) {
        return PromotionResponse.from(findEntity(promotionId));
    }

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

    @Transactional
    public PromotionResponse update(Long promotionId, PromotionRequest request) {
        Promotion promotion = findEntity(promotionId);
        applyRequest(promotion, request);
        return PromotionResponse.from(promotion);
    }

    @Transactional
    public PromotionResponse updateStatus(Long promotionId, boolean active) {
        Promotion promotion = findEntity(promotionId);
        promotion.setActive(active);
        return PromotionResponse.from(promotion);
    }

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

    private void applyRequest(Promotion promotion, PromotionRequest request) {
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