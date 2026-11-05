package com.baldev.airline.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base type for all expected, client-facing errors. Each subclass maps to exactly one HTTP status, and
 * carries a stable machine-readable {@code code} that clients can branch on (the human message may change).
 * The global exception handler renders these as RFC 9457 problem details.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    protected ApiException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }
}
