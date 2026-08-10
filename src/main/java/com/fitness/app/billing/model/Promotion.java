
package com.fitness.app.billing.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "promotion")
@Getter
@Setter
@NoArgsConstructor
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long promotionId;

    private String code;
    private String name;
    private String description;

    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    private BigDecimal discountValue;
    private LocalDate validFrom;
    private LocalDate validTo;
    private Integer maxUses;
    private Short maxUsesPerMember;

    private Long authorizedByUserId;
    private Instant authorizedAt;
    private Boolean active;
}