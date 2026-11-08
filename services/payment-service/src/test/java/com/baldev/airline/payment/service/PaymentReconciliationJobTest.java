package com.baldev.airline.payment.service;

import com.baldev.airline.payment.service.PaymentService.ReconcileOutcome;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentReconciliationJobTest {

    private final PaymentService service = mock(PaymentService.class);
    private final PaymentReconciliationJob job =
            new PaymentReconciliationJob(service, Duration.ofMinutes(2), Duration.ofHours(24), 50);

    @Test
    void oneFailingPaymentDoesNotStopTheOthers() {
        when(service.findPendingForReconciliation(Duration.ofMinutes(2), Duration.ofHours(24), 50))
                .thenReturn(List.of(1L, 2L, 3L));
        when(service.reconcile(1L)).thenReturn(ReconcileOutcome.SETTLED);
        when(service.reconcile(2L)).thenThrow(new IllegalStateException("provider unreachable"));
        when(service.reconcile(3L)).thenReturn(ReconcileOutcome.FAILED);

        job.run();

        verify(service).reconcile(3L); // still checked after payment 2 failed
    }

    @Test
    void nothingPendingMeansNoProviderCalls() {
        when(service.findPendingForReconciliation(eq(Duration.ofMinutes(2)), eq(Duration.ofHours(24)), anyInt()))
                .thenReturn(List.of());

        job.run();

        verify(service, never()).reconcile(org.mockito.ArgumentMatchers.anyLong());
    }
}
