package com.fitness.app.common.exception;

import lombok.Getter;

/**
 * A business rule was broken. The HTTP status and the message travel inside the
 * ErrorCode, so every throw site is a single line.
 */
@Getter
public class BusinessException extends RuntimeException
{
    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode)
    {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    /** Overrides the catalog message when the case needs concrete data in it. */
    public BusinessException(ErrorCode errorCode, String message)
    {
        super(message);
        this.errorCode = errorCode;
    }
}
