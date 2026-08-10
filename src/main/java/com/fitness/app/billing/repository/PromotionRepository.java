package com.fitness.app.billing.repository;

import com.fitness.app.billing.model.Promotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {

    Optional<Promotion> findByCode(String code);

    Page<Promotion> findByActive(Boolean active, Pageable pageable);
}