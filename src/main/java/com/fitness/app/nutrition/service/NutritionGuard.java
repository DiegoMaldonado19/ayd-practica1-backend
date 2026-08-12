package com.fitness.app.nutrition.service;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;
import com.fitness.app.directory.MemberService;
import com.fitness.app.iam.dto.AuthenticatedUser;
import com.fitness.app.iam.model.UserRole;
import com.fitness.app.membership.MembershipService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Centraliza la validación de acceso por miembro y la membresía activa
 * antes de consultar o editar datos nutricionales.
 */
@Component
@RequiredArgsConstructor
public class NutritionGuard
{
    private final MemberService     memberService;
    private final MembershipService membershipService;

    /** Devuelve el miembro válido para la operación según el rol del usuario. */
    public Long scopedMemberId(Long memberId, AuthenticatedUser principal)
    {
        if (principal.role() == UserRole.MEMBER && memberId == null)
        {
            return memberService.findOwnMemberId(principal);
        }

        if (memberId == null)
        {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "Debe indicar el socio (member_id).");
        }

        memberService.findById(memberId, principal);

        return memberId;
    }

    /** Rechaza la operación si la membresía del socio no está activa. */
    public void requireActiveMembership(Long memberId)
    {
        membershipService.findActiveMembership(memberId);
    }
}