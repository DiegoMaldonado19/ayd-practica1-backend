package com.fitness.app.billing.service;

import com.fitness.app.billing.dto.PaymentFilter;
import com.fitness.app.billing.dto.PaymentRequest;
import com.fitness.app.billing.dto.PaymentResponse;
import com.fitness.app.billing.dto.PaymentVoidRequest;
import com.fitness.app.billing.dto.ReceiptResponse;
import com.fitness.app.billing.model.DiscountType;
import com.fitness.app.billing.model.Payment;
import com.fitness.app.billing.model.PaymentConcept;
import com.fitness.app.billing.model.PaymentStatus;
import com.fitness.app.billing.model.Promotion;
import com.fitness.app.billing.repository.PaymentRepository;
import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.membership.MembershipService;
import com.fitness.app.membership.dto.MembershipResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PromotionService  promotionService;
    private final MembershipService membershipService;
    private final MemberService     memberService;
    private final EntityManager     entityManager;

    @Value("${gym.billing.receipt-series:A}")
    private String receiptSeries;

    /**
     * Lista pagos según filtros y alcance del usuario.
     *
     * @param filter    criterios de búsqueda
     * @param principal usuario autenticado
     * @param pageable  paginación
     * @return página de PaymentResponse
     */
    @Transactional(readOnly = true)
    public Page<PaymentResponse> list(PaymentFilter filter, AuthenticatedUser principal, Pageable pageable) {
        Long effectiveMemberId = resolveMemberIdFilter(filter.memberId(), principal);
        Specification<Payment> spec = buildSpecification(effectiveMemberId, filter);
        return paymentRepository.findAll(spec, pageable).map(PaymentResponse::from);
    }

    /**
     * Recupera un pago por id y valida si el usuario puede verlo.
     *
     * @param paymentId id del pago
     * @param principal usuario autenticado
     * @return PaymentResponse del pago
     */
    @Transactional(readOnly = true)
    public PaymentResponse findById(Long paymentId, AuthenticatedUser principal) {
        Payment payment = findEntity(paymentId);
        assertCanView(payment, principal);
        return PaymentResponse.from(payment);
    }

    /**
     * Obtiene el recibo de un pago confirmado, si ya existe.
     *
     * @param paymentId id del pago
     * @param principal usuario autenticado
     * @return ReceiptResponse con el recibo
     */
    @Transactional(readOnly = true)
    public ReceiptResponse getReceipt(Long paymentId, AuthenticatedUser principal) {
        Payment payment = findEntity(paymentId);
        assertCanView(payment, principal);
        if (payment.getReceiptNumber() == null) {
            throw new BusinessException(ErrorCode.PAYMENT_RECEIPT_NOT_AVAILABLE);
        }
        return ReceiptResponse.from(payment);
    }

    /**
     * Registra un nuevo pago, aplicando concepto, promoción y validaciones.
     *
     * @param request   datos del pago
     * @param principal usuario autenticado que registra el pago
     * @return PaymentResponse creado
     */
    @Transactional
    public PaymentResponse register(PaymentRequest request, AuthenticatedUser principal) {

        Payment payment = new Payment();
        payment.setConcept(request.concept());
        payment.setPaymentMethod(request.paymentMethod());
        payment.setStatus(PaymentStatus.REGISTERED);
        payment.setPaidAt(Instant.now());
        payment.setRegisteredByUserId(principal.appUserId());
        payment.setDiscountAmount(BigDecimal.ZERO);

        switch (request.concept()) {
            case MEMBERSHIP -> registerMembershipPayment(request, payment, principal);
            case GUEST_PASS  -> registerGuestPassPayment(request, payment);
            case OTHER       -> registerOtherPayment(request, payment, principal);
        }

        if (request.promotionId() != null) {
            Promotion promotion = promotionService.getActiveAndValid(request.promotionId());
            enforceUsageLimits(promotion, request.memberId());
            payment.setPromotion(promotion);
            payment.setDiscountAmount(calculateDiscount(promotion, payment.getGrossAmount()));
        }

        try {
            return PaymentResponse.from(paymentRepository.saveAndFlush(payment));
        } catch (DataIntegrityViolationException e) {
            // La FK fk_payment_guest_pass falla si el guest_pass_id no existe:
            if (request.concept() == PaymentConcept.GUEST_PASS) {
                throw new BusinessException(ErrorCode.GUEST_PASS_NOT_FOUND,
                        "El pase de invitado indicado no existe.");
            }
            throw e;
        }
    }

    private void registerMembershipPayment(PaymentRequest request, Payment payment, AuthenticatedUser principal) {
        if (request.memberId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "memberId es obligatorio cuando concept = MEMBERSHIP.");
        }
        if (request.membershipId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "membershipId es obligatorio cuando concept = MEMBERSHIP.");
        }

        MembershipResponse membership = membershipService.findById(request.membershipId(), principal);
        if (!membership.memberId().equals(request.memberId())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La membresía no pertenece al socio indicado.");
        }

        payment.setMemberId(membership.memberId());
        payment.setMembershipId(membership.membershipId());
        payment.setGrossAmount(membership.paidPrice());
    }

    private void registerGuestPassPayment(PaymentRequest request, Payment payment) {
        // El pagador es un visitante: persona, nunca socio (schema.sql ck_payment_target).
        if (request.guestPassId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "guestPassId es obligatorio cuando concept = GUEST_PASS.");
        }
        if (request.amount() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "amount es obligatorio cuando concept = GUEST_PASS.");
        }

        // Un pase se cobra una sola vez; un pago anulado (VOIDED) deja de contar.
        if (paymentRepository.countActiveByGuestPassId(request.guestPassId()) > 0) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_REGISTERED_FOR_PASS);
        }

        payment.setGuestPassId(request.guestPassId());
        payment.setMemberId(null);
        payment.setMembershipId(null);
        payment.setGrossAmount(request.amount());
    }

    private void registerOtherPayment(PaymentRequest request, Payment payment, AuthenticatedUser principal) {
        if (request.memberId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "memberId es obligatorio cuando concept = OTHER.");
        }
        if (request.amount() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "amount es obligatorio cuando concept = OTHER.");
        }

        memberService.findById(request.memberId(), principal);
        payment.setMemberId(request.memberId());
        payment.setGrossAmount(request.amount());
    }

    /**
     * Confirma un pago pendiente y asigna el siguiente número de recibo.
     *
     * @param paymentId id del pago
     * @return PaymentResponse confirmado
     */
    @Transactional
    public PaymentResponse confirm(Long paymentId) {
        Payment payment = findEntity(paymentId);
        if (payment.getStatus() == PaymentStatus.CONFIRMED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_CONFIRMED);
        }
        if (payment.getStatus() == PaymentStatus.VOIDED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_VOIDED);
        }
        acquireReceiptSeriesLock(receiptSeries);
        int nextNumber = paymentRepository.findMaxReceiptNumber(receiptSeries) + 1;

        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setReceiptSeries(receiptSeries);
        payment.setReceiptNumber(nextNumber);
        payment.setReceiptIssuedAt(Instant.now());

        return PaymentResponse.from(payment);
    }

    /**
     * Anula un pago y registra la razón de la anulación.
     *
     * @param paymentId id del pago
     * @param request   datos de la anulación
     * @return PaymentResponse actualizado
     */
    @Transactional
    public PaymentResponse voidPayment(Long paymentId, PaymentVoidRequest request) {
        Payment payment = findEntity(paymentId);
        if (payment.getStatus() == PaymentStatus.VOIDED) {
            throw new BusinessException(ErrorCode.PAYMENT_ALREADY_VOIDED);
        }
        payment.setStatus(PaymentStatus.VOIDED);
        payment.setVoidedAt(Instant.now());
        payment.setVoidReason(request.reason());
        return PaymentResponse.from(payment);
    }

    private Payment findEntity(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private void assertCanView(Payment payment, AuthenticatedUser principal) {
        if (isMember(principal)) {
            Long ownMemberId = memberService.findOwnMemberId(principal);
            if (!ownMemberId.equals(payment.getMemberId())) {
                throw new BusinessException(ErrorCode.FORBIDDEN_RESOURCE);
            }
        }
    }

    private Long resolveMemberIdFilter(Long requestedMemberId, AuthenticatedUser principal) {
        if (isMember(principal)) {
            return memberService.findOwnMemberId(principal);
        }
        return requestedMemberId;
    }

    private boolean isMember(AuthenticatedUser principal) {
        return "MEMBER".equals(principal.role().name());
    }

    private void enforceUsageLimits(Promotion promotion, Long memberId) {
        if (promotion.getMaxUses() != null) {
            long uses = paymentRepository.countActiveUsesByPromotion(promotion.getPromotionId());
            if (uses >= promotion.getMaxUses()) {
                throw new BusinessException(ErrorCode.PROMOTION_USES_EXCEEDED);
            }
        }
        // Un pago de invitado no tiene socio: el tope "por socio" solo aplica contra
        // un member_id real sin esto todos los pagos GUEST_PASS compartirían
        // el contador de member_id = null y se bloquearían entre si
        if (promotion.getMaxUsesPerMember() != null && memberId != null) {
            long usesByMember = paymentRepository.countActiveUsesByPromotionAndMember(
                    promotion.getPromotionId(), memberId);
            if (usesByMember >= promotion.getMaxUsesPerMember()) {
                throw new BusinessException(ErrorCode.PROMOTION_USES_EXCEEDED);
            }
        }
    }

    private BigDecimal calculateDiscount(Promotion promotion, BigDecimal grossAmount) {
        BigDecimal discount = promotion.getDiscountType() == DiscountType.PERCENTAGE ? grossAmount.multiply(promotion.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                : promotion.getDiscountValue();
        return discount.min(grossAmount);
    }

    private void acquireReceiptSeriesLock(String series) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:series))")
                .setParameter("series", series)
                .getSingleResult();
    }

    private Specification<Payment> buildSpecification(Long memberId, PaymentFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (memberId != null) {
                predicates.add(cb.equal(root.get("memberId"), memberId));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("paidAt"), filter.from().atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThan(root.get("paidAt"), filter.to().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.paymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), filter.paymentMethod()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
