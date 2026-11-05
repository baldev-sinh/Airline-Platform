package com.baldev.airline.common.exception;

import org.springframework.http.HttpStatus;

/** 400: the request is malformed or has invalid parameters. */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }
}
