package com.baldev.airline.payment.service;

import com.baldev.airline.common.enums.PaymentGateway;
import com.baldev.airline.common.enums.PaymentStatus;
import com.baldev.airline.common.event.PaymentRefundedEvent;
import com.baldev.airline.common.event.RefundFailedEvent;
import com.baldev.airline.common.event.RefundRequestedEvent;
import com.baldev.airline.common.exception.DownstreamServiceException;
import com.baldev.airline.payment.model.Payment;
import com.baldev.airline.payment.model.Refund;
import com.baldev.airline.payment.model.Refund.State;
import com.baldev.airline.payment.repository.PaymentRepository;
import com.baldev.airline.payment.repository.RefundRepository;
import com.baldev.airline.payment.service.gateway.PaymentProvider;
import com.baldev.airline.payment.service.gateway.PaymentProvider.RefundRejectedException;
import com.baldev.airline.payment.service.gateway.PaymentProvider.RefundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefundServiceTest {

    private final RefundRepository refunds = mock(RefundRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentProvider provider = mock(PaymentProvider.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private RefundService service;
    private Payment payment;
    private Refund stored;

    @BeforeEach
    void setUp() {
        service = new RefundService(refunds, payments, provider, events, new DirectTransactionTemplate(),
                Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC));
        payment = Payment.builder().id(11L).bookingId(21L).userId(5L).amount(new BigDecimal("1000.00")).currency("INR")
                .provider(PaymentGateway.RAZORPAY).status(PaymentStatus.SUCCESS).providerPaymentId("pay_1")
                .transactionId("TXN_1").build();
        when(payments.findByIdForUpdate(11L)).thenReturn(Optional.of(payment));
        when(payments.findById(11L)).thenReturn(Optional.of(payment));
        when(refunds.findByRefundKey(anyString())).thenReturn(Optional.empty());
        when(refunds.sumAmountByPaymentIdExcludingStatus(11L, State.FAILED)).thenReturn(BigDecimal.ZERO);
        when(refunds.saveAndFlush(any(Refund.class))).thenAnswer(inv -> {
            Refund r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(31L);
            }
            stored = r;
            return r;
        });
        when(refunds.transition(eq(31L), any(), any(), any())).thenReturn(1);
        when(refunds.findById(31L)).thenAnswer(inv -> Optional.of(stored));
        when(refunds.findByIdForUpdate(31L)).thenAnswer(inv -> Optional.of(stored));
    }

    @Test
    void processedRefundMarksPaymentRefundedAndPublishesOnce() {
        when(provider.refund("pay_1", 100000L, "booking-21-cancel")).thenReturn(new RefundResult("rfnd_1", "processed"));
        when(refunds.sumAmountByPaymentIdAndStatus(11L, State.PROCESSED)).thenReturn(new BigDecimal("1000.00"));

        service.requestRefund(request("booking-21-cancel", "1000.00"));

        assertThat(stored.getStatus()).isEqualTo(State.PROCESSED);
        assertThat(stored.getProviderRefundId()).isEqualTo("rfnd_1");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        ArgumentCaptor<Object> event = ArgumentCaptor.forClass(Object.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOf(PaymentRefundedEvent.class);
    }

    @Test
    void partialRefundLeavesPaymentPartiallyRefunded() {
        when(provider.refund("pay_1", 40000L, "k")).thenReturn(new RefundResult("rfnd_1", "processed"));
        when(refunds.sumAmountByPaymentIdAndStatus(11L, State.PROCESSED)).thenReturn(new BigDecimal("400.00"));

        service.requestRefund(request("k", "400.00"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void neverRefundsMoreThanWhatIsLeft() {
        when(refunds.sumAmountByPaymentIdExcludingStatus(11L, State.FAILED)).thenReturn(new BigDecimal("700.00"));
        when(provider.refund(anyString(), anyLong(), anyString())).thenReturn(new RefundResult("rfnd_2", "pending"));

        service.requestRefund(request("k", "1000.00"));

        verify(provider).refund("pay_1", 30000L, "k");
        assertThat(stored.getStatus()).isEqualTo(State.PROCESSING);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void duplicateRequestDoesNotCallTheProviderAgain() {
        Refund done = Refund.builder().id(31L).refundKey("k").status(State.PROCESSED).build();
        when(refunds.findByRefundKey("k")).thenReturn(Optional.of(done));

        service.requestRefund(request("k", "1000.00"));

        verify(provider, never()).refund(anyString(), anyLong(), anyString());
        verify(refunds, never()).saveAndFlush(any());
    }

    @Test
    void uncapturedPaymentFailsWithoutCallingTheProvider() {
        payment.setStatus(PaymentStatus.FAILED);

        service.requestRefund(request("k", "1000.00"));

        assertThat(stored.getStatus()).isEqualTo(State.FAILED);
        verify(provider, never()).refund(anyString(), anyLong(), anyString());
        verify(events).publishEvent(any(RefundFailedEvent.class));
    }

    @Test
    void providerRejectionFailsTheRefund() {
        when(provider.refund(anyString(), anyLong(), anyString()))
                .thenThrow(new RefundRejectedException("BAD_REQUEST_ERROR:too much", null));

        service.requestRefund(request("k", "1000.00"));

        assertThat(stored.getStatus()).isEqualTo(State.FAILED);
        verify(events).publishEvent(any(RefundFailedEvent.class));
    }

    @Test
    void providerOutageReleasesTheClaimForARetry() {
        when(provider.refund(anyString(), anyLong(), anyString()))
                .thenThrow(new DownstreamServiceException("down", null));

        service.requestRefund(request("k", "1000.00"));

        verify(refunds).transition(eq(31L), eq(State.SUBMITTING), eq(State.REQUESTED), any());
        verify(events, never()).publishEvent(any());
    }

    private static RefundRequestedEvent request(String key, String amount) {
        return RefundRequestedEvent.builder().refundKey(key).bookingId(21L).paymentId(11L).userId(5L)
                .amount(new BigDecimal(amount)).currency("INR").reason("test").build();
    }

    private static final class DirectTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(mock(TransactionStatus.class));
        }
    }
}
