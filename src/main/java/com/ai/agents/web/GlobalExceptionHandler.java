package com.ai.agents.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Turns exceptions into a uniform {@link ApiError} body so no endpoint leaks a stack trace or an
 * internal message. Client mistakes become 400s with actionable detail; anything unexpected becomes
 * a 500 carrying only a correlation id, with the full cause logged against that id server-side.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Bean-validation failures on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException e) {
        List<ApiError.FieldError> fields = e.getBindingResult().getFieldErrors().stream()
                .map(GlobalExceptionHandler::toFieldError)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "one or more fields are invalid", null, fields);
    }

    /** Malformed or unparseable JSON. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException e) {
        return build(HttpStatus.BAD_REQUEST, "request body is missing or malformed", null, List.of());
    }

    /** Bad arguments surfaced from service logic. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleIllegalArgument(IllegalArgumentException e) {
        return build(HttpStatus.BAD_REQUEST, safeMessage(e), null, List.of());
    }

    /** Anything unexpected: log the full cause against a correlation id, return only the id. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        String errorId = UUID.randomUUID().toString();
        log.error("Unhandled exception [errorId={}]", errorId, e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "an unexpected error occurred; quote this errorId when reporting it", errorId, List.of());
    }

    private static ApiError.FieldError toFieldError(FieldError f) {
        String message = f.getDefaultMessage() == null ? "invalid" : f.getDefaultMessage();
        return new ApiError.FieldError(f.getField(), message);
    }

    private static ResponseEntity<ApiError> build(HttpStatus status, String message, String errorId,
                                                  List<ApiError.FieldError> fields) {
        ApiError body = new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, errorId, fields);
        return ResponseEntity.status(status).body(body);
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? "invalid request" : e.getMessage();
    }
}
