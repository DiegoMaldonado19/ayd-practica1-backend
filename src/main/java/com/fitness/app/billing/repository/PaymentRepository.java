package com.fitness.app.billing.repository;

import com.fitness.app.billing.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long>,
        JpaSpecificationExecutor<Payment> {

    @Query("select count(p) from Payment p where p.promotion.promotionId = :promotionId and p.status <> 'VOIDED'")
    long countActiveUsesByPromotion(@Param("promotionId") Long promotionId);

    @Query("select count(p) from Payment p where p.promotion.promotionId = :promotionId "
            + "and p.memberId = :memberId and p.status <> 'VOIDED'")
    long countActiveUsesByPromotionAndMember(@Param("promotionId") Long promotionId,
                                              @Param("memberId") Long memberId);

    @Query("select coalesce(max(p.receiptNumber), 0) from Payment p where p.receiptSeries = :series")
    Integer findMaxReceiptNumber(@Param("series") String series);

    @Query("select count(p) from Payment p where p.guestPassId = :guestPassId and p.status <> 'VOIDED'")
    long countActiveByGuestPassId(@Param("guestPassId") Long guestPassId);
}