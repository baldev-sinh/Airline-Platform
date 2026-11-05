package com.baldev.airline.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 503: a dependency (another service or an external provider such as a payment gateway) failed or is
 * unavailable. Safe to retry. The message never includes the dependency's raw response body.
 */
public class DownstreamServiceException extends ApiException {

    public DownstreamServiceException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE", message);
    }

    public DownstreamServiceException(String message, Throwable cause) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE", message, cause);
    }
}
