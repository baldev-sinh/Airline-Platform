package com.baldev.airline.common.exception;

import org.springframework.http.HttpStatus;

/** 403: the caller is authenticated but not allowed to perform this action on this resource. */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
