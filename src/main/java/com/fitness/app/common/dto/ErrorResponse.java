package com.fitness.app.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fitness.app.common.exception.ErrorCode;

import java.time.Instant;
import java.util.Map;

/**
 * The single error body of the whole system. Field names are lowerCamelCase and
 * Jackson renders them in snake_case from the global naming strategy, so no
 * field carries a @JsonProperty.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String              errorCode,
                            String              message,
                            String              suggestedAction,
                            Instant             timestamp,
                            String              path,
                            String              traceId,
                            Map<String, String> fieldErrors)
{
    public static ErrorResponse of(ErrorCode errorCode, String path)
    {
        return of(errorCode, path, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String path, String message)
    {
        return new ErrorResponse(errorCode.name(), message, errorCode.getSuggestedAction(),
                                 Instant.now(), path, null, null);
    }

    /** For the unexpected error: the traceId leads straight to the log line. */
    public static ErrorResponse traced(ErrorCode errorCode, String path, String traceId)
    {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage(), errorCode.getSuggestedAction(),
                                 Instant.now(), path, traceId, null);
    }

    /** For Bean Validation: which field failed and why. */
    public static ErrorResponse validation(String path, Map<String, String> fieldErrors)
    {
        return new ErrorResponse(ErrorCode.VALIDATION_ERROR.name(), ErrorCode.VALIDATION_ERROR.getMessage(),
                                 null, Instant.now(), path, null, fieldErrors);
    }
}
