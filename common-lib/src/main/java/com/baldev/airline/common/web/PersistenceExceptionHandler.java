package com.baldev.airline.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps persistence-layer exceptions (duplicate keys, optimistic and pessimistic lock failures) to 409. Registered only in services that have spring-tx on the classpath,
 * and ordered before {@link GlobalExceptionHandler} so its catch-all does not win.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class PersistenceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return GlobalExceptionHandler.problem(HttpStatus.CONFLICT, "DATA_CONFLICT",
                "The request conflicts with existing data");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Concurrent modification: {}", ex.getMessage());
        return GlobalExceptionHandler.problem(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "The resource was modified concurrently; reload and retry");
    }

    /**
     * A row lock could not be taken: the lock-wait timeout expired, or the database chose this transaction as a
     * deadlock victim. The transaction has been rolled back, so retrying is safe.
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handlePessimisticLock(PessimisticLockingFailureException ex) {
        log.warn("Lock not acquired: {}", ex.getMessage());
        return GlobalExceptionHandler.problem(HttpStatus.CONFLICT, "LOCK_CONFLICT",
                "The resource is busy with another request; retry shortly");
    }
}
