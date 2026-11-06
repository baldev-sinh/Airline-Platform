package com.baldev.airline.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceExceptionHandlerTest {

    private final PersistenceExceptionHandler handler = new PersistenceExceptionHandler();

    @Test
    void lockWaitTimeoutOrDeadlockBecomesRetryableConflict() {
        // Spring translates MySQL 1205 (lock wait timeout) and 1213 (deadlock) into CannotAcquireLockException.
        ResponseEntity<ProblemDetail> response =
                handler.handlePessimisticLock(new CannotAcquireLockException("Lock wait timeout exceeded"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "LOCK_CONFLICT");
        // The database message is logged, never sent to the client.
        assertThat(response.getBody().getDetail()).doesNotContain("Lock wait timeout");
    }

    @Test
    void anyPessimisticLockFailureIsCovered() {
        ResponseEntity<ProblemDetail> response =
                handler.handlePessimisticLock(new PessimisticLockingFailureException("row locked"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
