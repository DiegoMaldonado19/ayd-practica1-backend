package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.nutrition.dto.MealItemRequest;
import com.fitness.app.nutrition.dto.MealItemResponse;
import com.fitness.app.nutrition.dto.MealRequest;
import com.fitness.app.nutrition.model.Food;
import com.fitness.app.nutrition.dto.MealResponse;
import com.fitness.app.nutrition.dto.MealUpdateRequest;
import com.fitness.app.nutrition.model.Meal;
import com.fitness.app.nutrition.model.MealItem;
import com.fitness.app.nutrition.repository.MealItemRepository;
import com.fitness.app.nutrition.repository.MealRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Administra las comidas del miembro, incluyendo validaciones de seguridad,
 * cálculos nutricionales y reglas de edición por día.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class MealService
{
    private static final LocalDate NO_LOWER_BOUND = LocalDate.of(1, 1, 1);
    private static final LocalDate NO_UPPER_BOUND = LocalDate.of(9999, 12, 31);

    private final MealRepository     mealRepository;
    private final MealItemRepository mealItemRepository;
    private final FoodService        foodService;
    private final NutritionGuard     guard;

    /** Busca comidas del miembro dentro de un rango de fechas y aplica permisos. */
    @Transactional(readOnly = true)
    public Page<MealResponse> search(Long memberId, LocalDate date, LocalDate from, LocalDate to,
                                     AuthenticatedUser principal, Pageable pageable)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        var effectiveFrom = date != null ? date : (from != null ? from : NO_LOWER_BOUND);
        var effectiveTo   = date != null ? date : (to != null ? to : NO_UPPER_BOUND);

        if (effectiveFrom.isAfter(effectiveTo))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La fecha inicial no puede ser posterior a la final.");
        }

        var page = mealRepository.search(scopedMemberId, effectiveFrom, effectiveTo, pageable);

        return new PageImpl<>(buildResponses(page.getContent()), page.getPageable(), page.getTotalElements());
    }

    /** Registra una comida nueva y guarda sus líneas de alimentos asociados. */
    public MealResponse create(MealRequest request, AuthenticatedUser principal)
    {
        var memberId = guard.scopedMemberId(request.memberId(), principal);
        guard.requireActiveMembership(memberId);
        assertDistinctFoods(request.items());

        var meal = new Meal();
        meal.setMemberId(memberId);
        meal.setLogDate(request.logDate() == null ? LocalDate.now() : request.logDate());
        meal.setMealType(request.mealType());
        meal.setNotes(request.notes());
        meal.setCreatedAt(Instant.now());
        mealRepository.save(meal);

        var items = new ArrayList<MealItem>();

        for (var itemRequest : request.items())
        {
            items.add(toItem(meal.getMealId(), itemRequest));
        }

        mealItemRepository.saveAll(items);

        return buildResponse(meal, items);
    }

    /** Recupera una comida con sus alimentos para visualizarla en detalle. */
    @Transactional(readOnly = true)
    public MealResponse findById(Long mealId, AuthenticatedUser principal)
    {
        var meal = findOrFail(mealId);

        guard.scopedMemberId(meal.getMemberId(), principal);
        guard.requireActiveMembership(meal.getMemberId());

        var items = mealItemRepository.findByMealIdOrderByMealItemId(mealId);

        return buildResponse(meal, items);
    }

    /** Edita una comida solo si el registro pertenece al día actual. */
    public MealResponse update(Long mealId, MealUpdateRequest request, AuthenticatedUser principal)
    {
        var meal = findOrFail(mealId);

        assertSameDay(meal.getLogDate());
        guard.scopedMemberId(meal.getMemberId(), principal);
        guard.requireActiveMembership(meal.getMemberId());
        assertDistinctFoods(request.items());

        meal.setMealType(request.mealType());
        meal.setNotes(request.notes());
        meal.setUpdatedAt(Instant.now());

        mealItemRepository.deleteByMealId(mealId);

        var items = new ArrayList<MealItem>();

        for (var itemRequest : request.items())
        {
            items.add(toItem(mealId, itemRequest));
        }

        mealItemRepository.saveAll(items);

        return buildResponse(meal, items);
    }

    /** Elimina una comida solo si está dentro del mismo día de registro. */
    public void delete(Long mealId, AuthenticatedUser principal)
    {
        var meal = findOrFail(mealId);

        assertSameDay(meal.getLogDate());
        guard.scopedMemberId(meal.getMemberId(), principal);
        guard.requireActiveMembership(meal.getMemberId());

        mealItemRepository.deleteByMealId(mealId);
        mealRepository.delete(meal);
    }

    private static void assertSameDay(LocalDate logDate)
    {
        if (!logDate.equals(LocalDate.now()))
        {
            throw new BusinessException(ErrorCode.MEAL_EDIT_WINDOW_CLOSED);
        }
    }

    private static void assertDistinctFoods(List<MealItemRequest> items)
    {
        var distinctFoodIds = items.stream().map(MealItemRequest::foodId).distinct().count();

        if (distinctFoodIds != items.size())
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El mismo alimento no puede repetirse en una comida.");
        }
    }

    private MealItem toItem(Long mealId, MealItemRequest itemRequest)
    {
        var food = foodService.findOrFail(itemRequest.foodId());

        if (!food.isActive())
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "El alimento '" + food.getName() + "' está desactivado y no puede usarse.");
        }

        var item = new MealItem();
        item.setMealId(mealId);
        item.setFoodId(food.getFoodId());
        item.setQuantity(itemRequest.quantity());

        return item;
    }

    private Meal findOrFail(Long mealId)
    {
        return mealRepository.findById(mealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEAL_NOT_FOUND));
    }

    private List<MealResponse> buildResponses(List<Meal> meals)
    {
        if (meals.isEmpty())
        {
            return List.of();
        }

        var mealIds = meals.stream().map(Meal::getMealId).toList();
        var itemsByMeal = mealItemRepository.findByMealIdIn(mealIds).stream()
                .collect(Collectors.groupingBy(MealItem::getMealId));
        var foods = foodService.findAllById(
                itemsByMeal.values().stream().flatMap(List::stream).map(MealItem::getFoodId).toList());

        return meals.stream()
                .map(meal -> buildResponse(meal, itemsByMeal.getOrDefault(meal.getMealId(), List.of()), foods))
                .toList();
    }

    private MealResponse buildResponse(Meal meal, List<MealItem> items)
    {
        return buildResponse(meal, items, foodService.findAllById(
                items.stream().map(MealItem::getFoodId).toList()));
    }

    private MealResponse buildResponse(Meal meal, List<MealItem> items, Map<Long, Food> foods)
    {
        var itemResponses = items.stream()
                .map(item -> toItemResponse(item, foods.get(item.getFoodId())))
                .toList();

        return new MealResponse(
            meal.getMealId(),
            meal.getMemberId(),
            meal.getLogDate(),
            meal.getMealType(),
            meal.getNotes(),
            meal.getCreatedAt(),
            meal.getUpdatedAt(),
            itemResponses,
            sum(itemResponses, MealItemResponse::calories),
            sum(itemResponses, MealItemResponse::proteinG),
            sum(itemResponses, MealItemResponse::carbohydratesG),
            sum(itemResponses, MealItemResponse::fatG)
        );
    }

    private static MealItemResponse toItemResponse(MealItem item, Food food)
    {
        return new MealItemResponse(
            item.getMealItemId(),
            item.getMealId(),
            item.getFoodId(),
            food.getName(),
            item.getQuantity(),
            food.getServingSize(),
            food.getServingUnit(),
            NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getCalories()),
            NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getProteinG()),
            NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getCarbohydratesG()),
            NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getFatG())
        );
    }

    private static BigDecimal sum(List<MealItemResponse> items,
                                  Function<MealItemResponse, BigDecimal> getter)
    {
        return items.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}