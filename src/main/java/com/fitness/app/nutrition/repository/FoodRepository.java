package com.fitness.app.nutrition.repository;

import com.fitness.app.nutrition.model.Food;
import com.fitness.app.nutrition.model.FoodCategory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FoodRepository extends JpaRepository<Food, Long>
{
    /** Verifica si ya existe un alimento con el mismo código. */
    boolean existsByCode(String code);

    /** Busca alimentos por categoria, texto y estado activo en el catálogo. */
    @Query("""
           SELECT f
             FROM Food f
            WHERE (:category IS NULL OR f.category = :category)
              AND (:active IS NULL OR f.active = :active)
              AND (:search = '' OR LOWER(f.name) LIKE LOWER(CONCAT('%', :search, '%'))
                                 OR LOWER(f.code) LIKE LOWER(CONCAT('%', :search, '%')))
           """)
    Page<Food> search(FoodCategory category, Boolean active, String search, Pageable pageable);
}