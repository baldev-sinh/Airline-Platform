package com.baldev.airline.payment.service;

import com.baldev.airline.payment.service.PaymentService.ReconcileOutcome;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Safety net for payments left pending. A payment is normally settled by the customer's browser coming back
 * (POST /api/payments/verify) or by the provider's webhook. If both miss it (the customer closed the tab, the
 * webhook arrived while the service was down), this job asks the provider directly every few minutes and settles
 * or fails the payment, exactly as those paths would.
 *
 * <p>Only payments older than {@code min-age} are checked, so a customer who is still paying is never raced, and
 * none older than {@code max-age}, so abandoned history isn't polled forever. Running on several instances at once
 * is safe: each payment is settled under a row lock and settled payments are skipped.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "airline.payment.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationJob {

    private final PaymentService paymentService;
    private final Duration minAge;
    private final Duration maxAge;
    private final int batchSize;

    public PaymentReconciliationJob(PaymentService paymentService,
                                    @Value("${airline.payment.reconciliation.min-age:PT2M}") Duration minAge,
                                    @Value("${airline.payment.reconciliation.max-age:PT24H}") Duration maxAge,
                                    @Value("${airline.payment.reconciliation.batch-size:50}") int batchSize) {
        this.paymentService = paymentService;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${airline.payment.reconciliation.interval:PT2M}",
            initialDelayString = "${airline.payment.reconciliation.initial-delay:PT1M}")
    public void run() {
        List<Long> pending = paymentService.findPendingForReconciliation(minAge, maxAge, batchSize);
        if (pending.isEmpty()) {
            return;
        }
        Map<ReconcileOutcome, Integer> outcomes = new EnumMap<>(ReconcileOutcome.class);
        int errors = 0;
        for (Long paymentId : pending) {
            try {
                outcomes.merge(paymentService.reconcile(paymentId), 1, Integer::sum);
            } catch (RuntimeException e) {
                // One payment's problem (e.g. the provider is briefly unreachable) must not stop the others;
                // it stays pending and is checked again on the next run.
                errors++;
                log.warn("Reconciling paymentId={} failed, will retry next run: {}", paymentId, e.getMessage());
            }
        }
        log.info("Payment reconciliation: checked={} settled={} failed={} stillOpen={} skipped={} errors={}",
                pending.size(), outcomes.getOrDefault(ReconcileOutcome.SETTLED, 0),
                outcomes.getOrDefault(ReconcileOutcome.FAILED, 0), outcomes.getOrDefault(ReconcileOutcome.STILL_OPEN, 0),
                outcomes.getOrDefault(ReconcileOutcome.SKIPPED, 0), errors);
    }
}
