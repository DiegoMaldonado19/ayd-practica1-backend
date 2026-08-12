package com.fitness.app.nutrition.model;

import jakarta.persistence.Column;
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
 * Representa un alimento del catálogo disponible para registrar comidas.
 * Sus valores nutricionales son por porción y se usan como base para calcular lo consumido.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
public class Food
{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long foodId;

    private String      code;
    private String      name;

    @Enumerated(EnumType.STRING)
    private FoodCategory category;

    private BigDecimal  servingSize;

    @Enumerated(EnumType.STRING)
    private ServingUnit servingUnit;

    private BigDecimal  calories;
    
    @Column(name = "protein_g")
    private BigDecimal  proteinG;
    
    @Column(name = "carbohydrates_g")
    private BigDecimal  carbohydratesG;

    @Column(name = "fat_g")
    private BigDecimal  fatG;

    private boolean     active;
    private Instant     createdAt;
}