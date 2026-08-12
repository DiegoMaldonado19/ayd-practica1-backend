package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.config.GymProperties;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.nutrition.dto.NutritionGoalRequest;
import com.fitness.app.nutrition.dto.NutritionGoalResponse;
import com.fitness.app.nutrition.model.GoalDefinedBy;
import com.fitness.app.nutrition.model.NutritionGoal;
import com.fitness.app.nutrition.repository.NutritionGoalRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Transactional
public class NutritionGoalService
{
    private final NutritionGoalRepository nutritionGoalRepository;
    private final NutritionGuard          guard;
    private final GymProperties           gymProperties;

    /** Recupera la meta activa del miembro y valida que pueda consultarla. */
    @Transactional(readOnly = true)
    public NutritionGoalResponse findCurrent(Long memberId, AuthenticatedUser principal)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        return nutritionGoalRepository.findByMemberIdAndEndDateIsNull(scopedMemberId)
                .map(NutritionGoalResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NUTRITION_GOAL_NOT_FOUND));
    }

    /** Cierra la meta vigente y crea una nueva versión con el valor actual. */
    public NutritionGoalResponse upsert(Long memberId, NutritionGoalRequest request, AuthenticatedUser principal)
    {
        var scopedMemberId = guard.scopedMemberId(memberId, principal);
        guard.requireActiveMembership(scopedMemberId);

        nutritionGoalRepository.closeCurrent(scopedMemberId, LocalDate.now());

        var definedBy = principal.role() == UserRole.TRAINER ? GoalDefinedBy.TRAINER : GoalDefinedBy.MEMBER;

        var goal = new NutritionGoal();
        goal.setMemberId(scopedMemberId);
        goal.setGoalType(request.goalType());
        goal.setDailyCalories(request.dailyCalories());
        goal.setTolerancePercent(request.tolerancePercent() == null
                ? new BigDecimal(gymProperties.nutrition().defaultTolerancePercent())
                : request.tolerancePercent());
        goal.setTargetWeightKg(request.targetWeightKg());
        goal.setDefinedBy(definedBy);
        goal.setDefinedByUserId(principal.appUserId());
        goal.setStartDate(LocalDate.now());

        return NutritionGoalResponse.from(nutritionGoalRepository.save(goal));
    }
}