package com.baldev.airline.common.exception;

import org.springframework.http.HttpStatus;

/** 401: the caller is not authenticated, or the credentials are invalid. */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException(String message) {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }
}
