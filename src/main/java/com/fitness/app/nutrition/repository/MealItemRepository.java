package com.fitness.app.nutrition.repository;

import com.fitness.app.nutrition.model.MealItem;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface MealItemRepository extends JpaRepository<MealItem, Long>
{
    /** Recupera las líneas de alimentos de una o varias comidas. */
    List<MealItem> findByMealIdIn(Collection<Long> mealIds);

    /** ¿Algún socio ya consumió este alimento? Decide si sus macros pueden cambiar. */
    boolean existsByFoodId(Long foodId);

    /** Obtiene los items en orden para una comida específica. */
    List<MealItem> findByMealIdOrderByMealItemId(Long mealId);

    /** Elimina todas las líneas de alimentos de una comida antes de reemplazarlas. */
    @Modifying
    @Query("DELETE FROM MealItem mi WHERE mi.mealId = :mealId")
    void deleteByMealId(Long mealId);
}