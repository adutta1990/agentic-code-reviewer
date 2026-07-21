package com.ai.agents.web;

import java.time.Instant;
import java.util.List;

/**
 * The uniform error body every endpoint returns on failure.
 *
 * <p>{@code errorId} correlates a client-visible failure with the full stack trace in the server
 * logs, so a 500 can be diagnosed without ever putting internal detail on the wire.
 *
 * @param timestamp when the error was produced.
 * @param status    HTTP status code.
 * @param error     short reason phrase.
 * @param message   a safe, human-readable summary.
 * @param errorId   correlation id present on 5xx; null on 4xx.
 * @param fieldErrors per-field validation messages; empty unless the failure was a validation error.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String errorId,
        List<FieldError> fieldErrors) {

    public ApiError {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /** A single rejected field. */
    public record FieldError(String field, String message) {
    }
}
