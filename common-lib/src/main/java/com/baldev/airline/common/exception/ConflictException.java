package com.baldev.airline.common.exception;

import org.springframework.http.HttpStatus;

/** 409: the request conflicts with current state (duplicate key, seat already taken, concurrent update). */
public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
