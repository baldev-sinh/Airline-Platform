package com.baldev.airline.common.exception;

import org.springframework.http.HttpStatus;

/** 422: the request is well-formed but violates a business rule (e.g. end date before start date). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, code, message);
    }
}
