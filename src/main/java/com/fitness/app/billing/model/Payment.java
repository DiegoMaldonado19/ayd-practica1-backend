// Payment.java
package com.fitness.app.billing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    private Long memberId;
    private Long membershipId;
    private Long guestPassId;

    @Enumerated(EnumType.STRING)
    private PaymentConcept concept;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    private BigDecimal grossAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    private BigDecimal discountAmount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private Instant paidAt;

    private String receiptSeries;
    private Integer receiptNumber;
    private Instant receiptIssuedAt;

    private Instant voidedAt;
    private String voidReason;

    private Long registeredByUserId;
}