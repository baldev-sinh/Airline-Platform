package com.baldev.airline.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Safety net for refunds: resubmits refunds whose provider call failed transiently (or whose instance died),
 * and asks the provider about refunds it accepted but has not finished, in case the webhook is missed.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "airline.payment.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class RefundReconciliationJob {

    private final RefundService refundService;
    private final Duration minAge;
    private final int batchSize;

    public RefundReconciliationJob(RefundService refundService,
                                   @Value("${airline.payment.refund-reconciliation.min-age:PT1M}") Duration minAge,
                                   @Value("${airline.payment.reconciliation.batch-size:50}") int batchSize) {
        this.refundService = refundService;
        this.minAge = minAge;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${airline.payment.reconciliation.interval:PT2M}",
            initialDelayString = "${airline.payment.reconciliation.initial-delay:PT1M}")
    public void run() {
        try {
            int handled = refundService.reconcile(minAge, batchSize);
            if (handled > 0) {
                log.info("Refund reconciliation handled {} refunds", handled);
            }
        } catch (RuntimeException e) {
            log.warn("Refund reconciliation failed, will retry next run: {}", e.getMessage());
        }
    }
}
