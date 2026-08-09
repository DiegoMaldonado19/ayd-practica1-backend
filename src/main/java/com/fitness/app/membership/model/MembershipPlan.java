package com.fitness.app.membership.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A plan of the catalog. Benefits are columns and not a benefit/plan_benefit pair
 * because the statement fixes which benefits exist and only lets the team add new
 * levels, and a new level is a row.
 *
 * tier governs two rules at once: which plan change is an upgrade, and who has
 * priority in the waitlist. weeklyClassLimit null means unlimited when classes are
 * included.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class MembershipPlan
{
    /** -1 is what MembershipService.weeklyClassLimit answers for a plan without a cap. */
    public static final int UNLIMITED_CLASSES = -1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long          membershipPlanId;

    private String        code;
    private String        name;
    private String        description;

    @Enumerated(EnumType.STRING)
    private BillingPeriod billingPeriod;

    private BigDecimal    price;
    private short         tier;
    private boolean       includesGroupClasses;
    private Short         weeklyClassLimit;
    private boolean       includesPersonalTrainer;
    private boolean       active;
    private Instant       createdAt;

    /**
     * The plan answers what it includes, so neither the service nor the response DTO
     * repeats the mapping between the benefit and its column.
     */
    public boolean includes(PlanBenefit benefit)
    {
        return switch (benefit)
        {
            case GROUP_CLASSES    -> includesGroupClasses;
            case PERSONAL_TRAINER -> includesPersonalTrainer;
        };
    }

    /** Unlimited while the plan includes classes and carries no cap. */
    public int effectiveWeeklyClassLimit()
    {
        return weeklyClassLimit == null ? UNLIMITED_CLASSES : weeklyClassLimit;
    }
}
