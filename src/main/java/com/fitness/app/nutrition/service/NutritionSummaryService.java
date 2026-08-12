package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.nutrition.dto.DailyTotals;
import com.fitness.app.nutrition.dto.MealTimeSummary;
import com.fitness.app.nutrition.dto.NutritionGoalResponse;
import com.fitness.app.nutrition.dto.NutritionSummaryResponse;
import com.fitness.app.nutrition.model.CalorieStatus;
import com.fitness.app.nutrition.model.Meal;
import com.fitness.app.nutrition.model.Food;
import com.fitness.app.nutrition.model.MealItem;
import com.fitness.app.nutrition.model.MealType;
import com.fitness.app.nutrition.model.NutritionGoal;
import com.fitness.app.nutrition.repository.MealItemRepository;
import com.fitness.app.nutrition.repository.MealRepository;
import com.fitness.app.nutrition.repository.NutritionGoalRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NutritionSummaryService
{
    private final MealRepository          mealRepository;
    private final MealItemRepository      mealItemRepository;
    private final FoodService             foodService;
    private final NutritionGoalRepository nutritionGoalRepository;
    private final NutritionGuard          guard;

    /** Calcula el resumen nutricional del día para un miembro autorizado. */
    public NutritionSummaryResponse daily(Long memberId, LocalDate date, AuthenticatedUser principal)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        var meals = mealRepository.findByMemberIdAndLogDateOrderByMealId(scopedMemberId, date);

        return build(scopedMemberId, date, meals);
    }

    /** Genera un resumen por día para un rango de fechas, ordenado de antiguo a nuevo. */
    public List<NutritionSummaryResponse> trend(Long memberId, LocalDate from, LocalDate to,
                                                AuthenticatedUser principal)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        if (from.isAfter(to))
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "La fecha inicial no puede ser posterior a la final.");
        }

        return mealRepository.findByMemberIdAndLogDateBetweenOrderByLogDateAscMealIdAsc(
                        scopedMemberId, from, to)
                .stream()
                .collect(Collectors.groupingBy(Meal::getLogDate, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .map(entry -> build(scopedMemberId, entry.getKey(), entry.getValue()))
                .toList();
    }

    private NutritionSummaryResponse build(Long memberId, LocalDate date, List<Meal> meals)
    {
        var byMeal = mealContributions(meals);
        var totals = totals(byMeal);
        var byMealTime = byMealTime(meals, byMeal);

        var goal = nutritionGoalRepository.findByMemberIdAndEndDateIsNull(memberId).orElse(null);
        var goalResponse = goal == null ? null : NutritionGoalResponse.from(goal);
        var evaluation = evaluate(totals.calories(), goal);

        return new NutritionSummaryResponse(
            memberId,
            date,
            totals,
            byMealTime,
            goalResponse,
            evaluation.status(),
            evaluation.difference(),
            evaluation.percentOfGoal()
        );
    }

    /** One Contribution per meal: the sum of its lines, computed on the fly. */
    private Map<Long, Contribution> mealContributions(List<Meal> meals)
    {
        if (meals.isEmpty())
        {
            return Map.of();
        }

        var mealIds = meals.stream().map(Meal::getMealId).toList();
        var items = mealItemRepository.findByMealIdIn(mealIds);

        if (items.isEmpty())
        {
            return Map.of();
        }

        var foods = foodService.findAllById(
                items.stream().map(MealItem::getFoodId).distinct().toList());

        return items.stream()
                .collect(Collectors.groupingBy(MealItem::getMealId))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(item -> contribution(item, foods.get(item.getFoodId())))
                                .reduce(Contribution.ZERO, Contribution::add)));
    }

    private static DailyTotals totals(Map<Long, Contribution> byMeal)
    {
        var total = byMeal.values().stream()
                .reduce(Contribution.ZERO, Contribution::add);

        return new DailyTotals(total.calories(), total.proteinG(), total.carbohydratesG(), total.fatG());
    }

    private static List<MealTimeSummary> byMealTime(List<Meal> meals, Map<Long, Contribution> byMeal)
    {
        var mealsByType = meals.stream().collect(Collectors.groupingBy(Meal::getMealType));

        return Arrays.stream(MealType.values())
                .map(type ->
                {
                    var typeMeals = mealsByType.getOrDefault(type, List.of());

                    if (typeMeals.isEmpty())
                    {
                        return null;
                    }

                    var total = typeMeals.stream()
                            .map(meal -> byMeal.getOrDefault(meal.getMealId(), Contribution.ZERO))
                            .reduce(Contribution.ZERO, Contribution::add);

                    return new MealTimeSummary(type, typeMeals.size(),
                            total.calories(), total.proteinG(), total.carbohydratesG(), total.fatG());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    /** Compara el consumo del día con la meta y determina si está por debajo, dentro o encima. */
    private static Evaluation evaluate(BigDecimal consumed, NutritionGoal goal)
    {
        if (goal == null)
        {
            return Evaluation.NONE;
        }

        var target = goal.getDailyCalories();
        var tolerance = goal.getTolerancePercent().multiply(target)
                .divide(NutritionMath.HUNDRED, 2, RoundingMode.HALF_UP);
        var lower = target.subtract(tolerance);
        var upper = target.add(tolerance);

        var status = consumed.compareTo(lower) < 0 ? CalorieStatus.UNDER
                   : consumed.compareTo(upper) > 0 ? CalorieStatus.OVER
                   : CalorieStatus.ACCEPTABLE;

        var percent = consumed.multiply(NutritionMath.HUNDRED)
                .divide(target, 2, RoundingMode.HALF_UP);

        return new Evaluation(status, consumed.subtract(target), percent);
    }

    private static Contribution contribution(MealItem item, Food food)
    {
        return new Contribution(
                NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getCalories()),
                NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getProteinG()),
                NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getCarbohydratesG()),
                NutritionMath.contribution(item.getQuantity(), food.getServingSize(), food.getFatG()));
    }

    private record Contribution(BigDecimal calories,
                                BigDecimal proteinG,
                                BigDecimal carbohydratesG,
                                BigDecimal fatG)
    {
        private static final Contribution ZERO =
                new Contribution(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);

        private Contribution add(Contribution other)
        {
            return new Contribution(calories.add(other.calories),
                                    proteinG.add(other.proteinG),
                                    carbohydratesG.add(other.carbohydratesG),
                                    fatG.add(other.fatG));
        }
    }

    private record Evaluation(CalorieStatus status, BigDecimal difference, BigDecimal percentOfGoal)
    {
        private static final Evaluation NONE =
                new Evaluation(null, null, null);
    }
}