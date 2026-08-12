package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.nutrition.dto.FoodRequest;
import com.fitness.app.nutrition.dto.FoodResponse;
import com.fitness.app.nutrition.model.Food;
import com.fitness.app.nutrition.model.FoodCategory;
import com.fitness.app.nutrition.repository.FoodRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FoodService
{
    private final FoodRepository foodRepository;

    /** Busca alimentos con filtros de categoria, texto y estado activo. */
    @Transactional(readOnly = true)
    public Page<FoodResponse> search(FoodCategory category, String search, Boolean active, Pageable pageable)
    {
        return foodRepository.search(category, active, search == null ? "" : search, pageable)
                .map(FoodResponse::from);
    }

    /** Guarda un alimento nuevo y valida que el código sea único. */
    public FoodResponse create(FoodRequest request)
    {
        if (foodRepository.existsByCode(request.code()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Ya existe un alimento con ese código.");
        }

        var food = new Food();
        food.setCode(request.code());
        food.setName(request.name());
        food.setCategory(request.category());
        food.setServingSize(request.servingSize());
        food.setServingUnit(request.servingUnit());
        food.setCalories(request.calories());
        food.setProteinG(request.proteinG());
        food.setCarbohydratesG(request.carbohydratesG());
        food.setFatG(request.fatG());
        food.setActive(true);
        food.setCreatedAt(Instant.now());

        return FoodResponse.from(foodRepository.save(food));
    }

    /** Busca un alimento por id para mostrarlo o usarlo en otras operaciones. */
    @Transactional(readOnly = true)
    public FoodResponse findById(Long foodId)
    {
        return FoodResponse.from(findOrFail(foodId));
    }

    /** Actualiza la información de un alimento y evita duplicados de código. */
    public FoodResponse update(Long foodId, FoodRequest request)
    {
        var food = findOrFail(foodId);

        if (!request.code().equals(food.getCode()) && foodRepository.existsByCode(request.code()))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Ya existe un alimento con ese código.");
        }

        food.setCode(request.code());
        food.setName(request.name());
        food.setCategory(request.category());
        food.setServingSize(request.servingSize());
        food.setServingUnit(request.servingUnit());
        food.setCalories(request.calories());
        food.setProteinG(request.proteinG());
        food.setCarbohydratesG(request.carbohydratesG());
        food.setFatG(request.fatG());

        return FoodResponse.from(food);
    }

    /** Desactiva el alimento para preservar el historial de comidas ya registradas. */
    public void deactivate(Long foodId)
    {
        findOrFail(foodId).setActive(false);
    }

    /**
     * Package-private for the module's services: a meal line references the catalog.
     * The active check is done by the caller, which carries the exact context.
     */
    Food findOrFail(Long foodId)
    {
        return foodRepository.findById(foodId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOOD_NOT_FOUND));
    }

    /** Package-private bulk lookup for the module's summary builders. */
    Map<Long, Food> findAllById(Collection<Long> foodIds)
    {
        if (foodIds.isEmpty())
        {
            return Map.of();
        }

        return foodRepository.findAllById(foodIds).stream()
                .collect(Collectors.toMap(Food::getFoodId, food -> food));
    }
}