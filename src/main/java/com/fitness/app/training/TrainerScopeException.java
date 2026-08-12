package com.fitness.app.training;

import com.fitness.app.common.exception.BusinessException;
import com.fitness.app.common.exception.ErrorCode;

/**
 * "Un entrenador solo puede leer datos de los socios que tiene asignados" 
 *
 */
public class TrainerScopeException extends BusinessException
{
    public TrainerScopeException()
    {
        super(ErrorCode.TRAINER_SCOPE_VIOLATION);
    }
}