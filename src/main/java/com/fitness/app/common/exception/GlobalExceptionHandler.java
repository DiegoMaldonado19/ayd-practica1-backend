package com.fitness.app.common.exception;

import com.fitness.app.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns every exception into the single ErrorResponse body of 03-API-REST §2.
 *
 * The exception is always the last argument to SLF4J and never a format
 * parameter: that is what makes SLF4J print the full stack trace, and the trace
 * is what carries the file and line number of every frame (CLAUDE.md §6).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler
{
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest request)
    {
        var errorCode = ex.getErrorCode();

        log.warn("errorCode={} method={} path={} message={}",
                 errorCode, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.status(errorCode.getHttpStatus())
                .body(ErrorResponse.of(errorCode, request.getRequestURI(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request)
    {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                                          DefaultMessageSourceResolvable::getDefaultMessage,
                                          (first, second) -> first));

        log.warn("errorCode={} method={} path={} fields={}",
                 ErrorCode.VALIDATION_ERROR, request.getMethod(), request.getRequestURI(), fieldErrors, ex);

        return ResponseEntity.badRequest()
                .body(ErrorResponse.validation(request.getRequestURI(), fieldErrors));
    }

    /** Malformed or missing JSON body: a client mistake, not a server failure. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request)
    {
        log.warn("errorCode={} method={} path={} message={}",
                 ErrorCode.VALIDATION_ERROR, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.badRequest()
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, request.getRequestURI(),
                                       "El cuerpo de la solicitud no es un JSON válido."));
    }

    /** Unknown route: without this it would fall into handleUnexpected and answer 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleUnknownRoute(NoResourceFoundException ex,
                                                            HttpServletRequest request)
    {
        log.warn("method={} path={} message={}",
                 request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ErrorCode.ROUTE_NOT_FOUND, request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request)
    {
        var traceId = UUID.randomUUID().toString();

        log.error("traceId={} method={} path={} message={}",
                  traceId, request.getMethod(), request.getRequestURI(), ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.traced(ErrorCode.INTERNAL_ERROR, request.getRequestURI(), traceId));
    }
}
