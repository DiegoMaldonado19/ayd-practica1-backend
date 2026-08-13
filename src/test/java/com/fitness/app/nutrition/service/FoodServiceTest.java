package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.nutrition.dto.FoodRequest;
import com.fitness.app.nutrition.model.Food;
import com.fitness.app.nutrition.model.FoodCategory;
import com.fitness.app.nutrition.model.ServingUnit;
import com.fitness.app.nutrition.repository.FoodRepository;
import com.fitness.app.nutrition.repository.MealItemRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * meal_item stores no snapshot of the macros: a meal recomputes them against the
 * current row of food, so editing the catalogue used to rewrite meals already logged.
 * The rule that closes it is that a food already eaten has frozen nutritional values.
 */
@ExtendWith(MockitoExtension.class)
class FoodServiceTest
{
    private static final Long FOOD_ID = 38L;

    @Mock private FoodRepository     foodRepository;
    @Mock private MealItemRepository mealItemRepository;

    @InjectMocks private FoodService foodService;

    @Test
    void refusesToChangeTheCaloriesOfAFoodSomebodyAlreadyAte()
    {
        when(foodRepository.findById(FOOD_ID)).thenReturn(Optional.of(food()));
        when(mealItemRepository.existsByFoodId(FOOD_ID)).thenReturn(true);

        assertEquals(ErrorCode.FOOD_IN_USE,
                     assertThrows(BusinessException.class,
                                  () -> foodService.update(FOOD_ID, request("200.00", "999.00"))).getErrorCode());
    }

    @Test
    void allowsRenamingAFoodAlreadyEatenBecauseTheNameIsNotInTheCalculation()
    {
        var stored = food();

        when(foodRepository.findById(FOOD_ID)).thenReturn(Optional.of(stored));

        var renamed = new FoodRequest(stored.getCode(), "Arroz blanco cocido (corregido)",
                                      stored.getCategory(), stored.getServingSize(), stored.getServingUnit(),
                                      stored.getCalories(), stored.getProteinG(),
                                      stored.getCarbohydratesG(), stored.getFatG());

        foodService.update(FOOD_ID, renamed);

        assertEquals("Arroz blanco cocido (corregido)", stored.getName());
    }

    @Test
    void allowsCorrectingTheCaloriesWhileNobodyHasEatenIt()
    {
        var stored = food();

        when(foodRepository.findById(FOOD_ID)).thenReturn(Optional.of(stored));
        when(mealItemRepository.existsByFoodId(FOOD_ID)).thenReturn(false);

        foodService.update(FOOD_ID, request("200.00", "999.00"));

        assertEquals(0, new BigDecimal("999.00").compareTo(stored.getCalories()));
    }

    /** 200 and 200.00 are not equal for BigDecimal.equals; an unchanged value must not trip the guard. */
    @Test
    void theSameValueWrittenWithAnotherScaleIsNotAChange()
    {
        var stored = food();

        when(foodRepository.findById(FOOD_ID)).thenReturn(Optional.of(stored));

        foodService.update(FOOD_ID, request("200", "200"));

        assertEquals(0, new BigDecimal("200").compareTo(stored.getCalories()));
    }

    private static Food food()
    {
        var food = new Food();

        food.setFoodId(FOOD_ID);
        food.setCode("ARROZ_100");
        food.setName("Arroz blanco cocido");
        food.setCategory(FoodCategory.CARBOHYDRATE);
        food.setServingSize(new BigDecimal("100.00"));
        food.setServingUnit(ServingUnit.GRAM);
        food.setCalories(new BigDecimal("200.00"));
        food.setProteinG(new BigDecimal("30.00"));
        food.setCarbohydratesG(new BigDecimal("10.00"));
        food.setFatG(new BigDecimal("5.00"));
        food.setActive(true);

        return food;
    }

    /** The stored food with servingSize and calories replaced. */
    private static FoodRequest request(String servingSize, String calories)
    {
        return new FoodRequest("ARROZ_100", "Arroz blanco cocido", FoodCategory.CARBOHYDRATE,
                               new BigDecimal(servingSize), ServingUnit.GRAM,
                               new BigDecimal(calories), new BigDecimal("30.00"),
                               new BigDecimal("10.00"), new BigDecimal("5.00"));
    }
}
