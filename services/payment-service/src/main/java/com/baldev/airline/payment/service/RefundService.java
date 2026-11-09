package com.baldev.airline.payment.service;

import com.baldev.airline.common.event.RefundRequestedEvent;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.common.event.PaymentRefundedEvent;
import com.baldev.airline.common.event.RefundFailedEvent;
import com.baldev.airline.common.enums.PaymentStatus;
import com.baldev.airline.payment.model.Payment;
import com.baldev.airline.payment.model.Refund;
import com.baldev.airline.payment.model.Refund.State;
import com.baldev.airline.payment.repository.PaymentRepository;
import com.baldev.airline.payment.repository.RefundRepository;
import com.baldev.airline.payment.service.gateway.PaymentProvider;
import com.baldev.airline.payment.service.gateway.PaymentProvider.RefundRejectedException;
import com.baldev.airline.payment.service.gateway.PaymentProvider.RefundResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Refunds captured payments on request (refund.requested from booking-service).
 *
 * <p>Rules: a refund never exceeds what was captured minus what is already refunded or on its way; each
 * {@code refundKey} refunds at most once, here (unique key + atomic claim) and at the provider (key stored in the
 * provider's refund notes). Provider calls are made outside database transactions. Outcomes are published
 * through the outbox as payment.refunded or payment.refund-failed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    /** A SUBMITTING claim older than this belongs to an instance that died mid-call; it is retried. */
    static final Duration STALE_SUBMISSION = Duration.ofMinutes(5);

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    /** Records the refund (idempotent per refund key) and submits it to the provider. */
    public void requestRefund(RefundRequestedEvent request) {
        Long refundId;
        try {
            refundId = transactionTemplate.execute(status -> record(request));
        } catch (DataIntegrityViolationException e) {
            log.info("Refund {} recorded concurrently by another consumer", request.getRefundKey());
            return;
        }
        if (refundId != null) {
            submit(refundId);
        }
    }

    private Long record(RefundRequestedEvent request) {
        Optional<Refund> existing = refundRepository.findByRefundKey(request.getRefundKey());
        if (existing.isPresent()) {
            log.info("Duplicate refund request {} (refund {} is {})", request.getRefundKey(),
                    existing.get().getId(), existing.get().getStatus());
            return existing.get().getStatus() == State.REQUESTED ? existing.get().getId() : null;
        }
        Payment payment = paymentRepository.findByIdForUpdate(request.getPaymentId()).orElse(null);
        String problem = validate(request, payment);
        BigDecimal amount = Money.normalize(request.getAmount());
        if (problem == null) {
            BigDecimal alreadyRefunded = refundRepository.sumAmountByPaymentIdExcludingStatus(payment.getId(), State.FAILED);
            BigDecimal remaining = Money.normalize(payment.getAmount().subtract(alreadyRefunded));
            if (remaining.signum() <= 0) {
                problem = "Payment is already fully refunded";
            } else if (amount.compareTo(remaining) > 0) {
                log.warn("Refund {} asks for {} but only {} is refundable; refunding {}", request.getRefundKey(),
                        amount, remaining, remaining);
                amount = remaining;
            }
        }
        Refund refund = refundRepository.saveAndFlush(Refund.builder()
                .refundKey(request.getRefundKey())
                .paymentId(request.getPaymentId())
                .bookingId(request.getBookingId())
                .userId(request.getUserId())
                .amount(amount)
                .currency(request.getCurrency() != null ? request.getCurrency() : payment != null ? payment.getCurrency() : "INR")
                .reason(truncate(request.getReason(), 255))
                .status(State.REQUESTED)
                .build());
        if (problem != null) {
            fail(refund, problem);
            return null;
        }
        log.info("Refund {} recorded: {} {} of payment {}", refund.getRefundKey(), amount, refund.getCurrency(),
                refund.getPaymentId());
        return refund.getId();
    }

    private static String validate(RefundRequestedEvent request, Payment payment) {
        if (payment == null) {
            return "Payment " + request.getPaymentId() + " does not exist";
        }
        if (!payment.getBookingId().equals(request.getBookingId())) {
            return "Payment " + payment.getId() + " does not belong to booking " + request.getBookingId();
        }
        if (!payment.isRefundable() || payment.getProviderPaymentId() == null) {
            return "Payment " + payment.getId() + " is " + payment.getStatus() + " and cannot be refunded";
        }
        if (request.getCurrency() != null && !request.getCurrency().equalsIgnoreCase(payment.getCurrency())) {
            return "Refund currency " + request.getCurrency() + " differs from payment currency " + payment.getCurrency();
        }
        if (request.getAmount() == null || request.getAmount().signum() <= 0) {
            return "Refund amount must be positive";
        }
        return null;
    }

    /**
     * Sends a REQUESTED refund to the provider, if this caller wins the claim. Transient provider errors put it
     * back to REQUESTED for the reconciliation job; a permanent rejection fails it.
     */
    public void submit(Long refundId) {
        Integer claimed = transactionTemplate.execute(status ->
                refundRepository.transition(refundId, State.REQUESTED, State.SUBMITTING, clock.instant()));
        if (claimed == null || claimed == 0) {
            return; // someone else is submitting it, or it is past that stage
        }
        Refund refund = refundRepository.findById(refundId).orElseThrow();
        Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();

        RefundResult result;
        try {
            result = paymentProvider.refund(payment.getProviderPaymentId(), Money.toMinorUnits(refund.getAmount()),
                    refund.getRefundKey());
        } catch (RefundRejectedException e) {
            transactionTemplate.executeWithoutResult(status -> refundRepository.findByIdForUpdate(refundId)
                    .filter(r -> !r.isTerminal())
                    .ifPresent(r -> fail(r, "Rejected by the payment provider: " + e.getMessage())));
            return;
        } catch (RuntimeException e) { // DownstreamServiceException (provider unreachable) and anything unexpected
            transactionTemplate.executeWithoutResult(status ->
                    refundRepository.transition(refundId, State.SUBMITTING, State.REQUESTED, clock.instant()));
            log.warn("Refund {} could not be submitted, will retry: {}", refund.getRefundKey(), e.getMessage());
            return;
        }
        transactionTemplate.executeWithoutResult(status -> refundRepository.findByIdForUpdate(refundId)
                .filter(r -> !r.isTerminal())
                .ifPresent(r -> {
                    r.setProviderRefundId(result.id());
                    apply(r, result.status(), null);
                }));
    }

    /**
     * Applies a status reported by the provider (refund.processed / refund.failed webhook). The refund is found by
     * provider id, or by our key from the refund's notes if the webhook beat our own bookkeeping.
     */
    public void onProviderUpdate(String providerRefundId, String refundKey, String status, String failureReason) {
        transactionTemplate.executeWithoutResult(tx -> {
            Optional<Refund> found = providerRefundId != null
                    ? refundRepository.findByProviderRefundIdForUpdate(providerRefundId)
                    : Optional.empty();
            if (found.isEmpty() && refundKey != null) {
                found = refundRepository.findByRefundKeyForUpdate(refundKey);
            }
            if (found.isEmpty()) {
                log.warn("Provider update for unknown refund {} ({})", providerRefundId, refundKey);
                return;
            }
            Refund refund = found.get();
            if (refund.isTerminal()) {
                return;
            }
            if (refund.getProviderRefundId() == null) {
                refund.setProviderRefundId(providerRefundId);
            }
            apply(refund, status, failureReason);
        });
    }

    /** Safety net: resubmits refunds that were never sent, and asks the provider about pending ones. */
    public int reconcile(Duration minAge, int limit) {
        Instant now = clock.instant();
        // Claims abandoned by an instance that died mid-call go back to REQUESTED; the provider-side key lookup
        // makes the resubmission safe even if the first call did reach the provider.
        for (Long id : refundRepository.findIdsInStatesUpdatedBefore(List.of(State.SUBMITTING),
                now.minus(STALE_SUBMISSION), PageRequest.of(0, limit))) {
            transactionTemplate.executeWithoutResult(s -> refundRepository.transition(id, State.SUBMITTING, State.REQUESTED, now));
        }
        int handled = 0;
        for (Long id : refundRepository.findIdsInStatesUpdatedBefore(List.of(State.REQUESTED), now.minus(minAge),
                PageRequest.of(0, limit))) {
            submit(id);
            handled++;
        }
        for (Long id : refundRepository.findIdsInStatesUpdatedBefore(List.of(State.PROCESSING), now.minus(minAge),
                PageRequest.of(0, limit))) {
            Refund refund = refundRepository.findById(id).orElse(null);
            if (refund == null || refund.getProviderRefundId() == null) {
                continue;
            }
            Payment payment = paymentRepository.findById(refund.getPaymentId()).orElseThrow();
            RefundResult result = paymentProvider.fetchRefund(payment.getProviderPaymentId(), refund.getProviderRefundId());
            onProviderUpdate(result.id(), refund.getRefundKey(), result.status(), null);
            handled++;
        }
        return handled;
    }

    private void apply(Refund refund, String providerStatus, String failureReason) {
        if ("processed".equalsIgnoreCase(providerStatus)) {
            complete(refund);
        } else if ("failed".equalsIgnoreCase(providerStatus)) {
            fail(refund, failureReason != null ? failureReason : "Refund failed at the payment provider");
        } else {
            refund.setStatus(State.PROCESSING); // pending at the provider: webhook or reconciliation completes it
        }
    }

    private void complete(Refund refund) {
        refund.setStatus(State.PROCESSED);
        refund.setProcessedAt(clock.instant());
        refundRepository.saveAndFlush(refund);

        Payment payment = paymentRepository.findByIdForUpdate(refund.getPaymentId()).orElseThrow();
        BigDecimal refunded = refundRepository.sumAmountByPaymentIdAndStatus(payment.getId(), State.PROCESSED);
        payment.setStatus(refunded.compareTo(payment.getAmount()) >= 0 ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        payment.setRefundId(refund.getProviderRefundId());

        eventPublisher.publishEvent(PaymentRefundedEvent.builder()
                .refundKey(refund.getRefundKey())
                .paymentId(refund.getPaymentId())
                .bookingId(refund.getBookingId())
                .userId(refund.getUserId())
                .amount(refund.getAmount())
                .currency(refund.getCurrency())
                .providerRefundId(refund.getProviderRefundId())
                .refundedAt(refund.getProcessedAt())
                .build());
        log.info("Refund {} processed: {} {} ({})", refund.getRefundKey(), refund.getAmount(), refund.getCurrency(),
                refund.getProviderRefundId());
    }

    private void fail(Refund refund, String reason) {
        refund.setStatus(State.FAILED);
        refund.setFailureReason(truncate(reason, 500));
        eventPublisher.publishEvent(RefundFailedEvent.builder()
                .refundKey(refund.getRefundKey())
                .paymentId(refund.getPaymentId())
                .bookingId(refund.getBookingId())
                .reason(refund.getFailureReason())
                .failedAt(clock.instant())
                .build());
        log.error("ACTION REQUIRED: refund {} for booking {} failed: {}", refund.getRefundKey(), refund.getBookingId(), reason);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
