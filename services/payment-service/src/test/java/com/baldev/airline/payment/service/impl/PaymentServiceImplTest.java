package com.baldev.airline.payment.service.impl;

import com.baldev.airline.common.enums.PaymentGateway;
import com.baldev.airline.common.enums.PaymentStatus;
import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.exception.UnauthorizedException;
import com.baldev.airline.common.payload.request.PaymentVerifyRequest;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.payment.client.UserClient;
import com.baldev.airline.payment.config.PaymentProperties;
import com.baldev.airline.payment.model.Payment;
import com.baldev.airline.payment.repository.PaymentRepository;
import com.baldev.airline.payment.service.gateway.PaymentProvider;
import com.baldev.airline.payment.service.gateway.PaymentProvider.GatewayPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentServiceImplTest {

    private static final CurrentUser OWNER = new CurrentUser(5L, "o@x.com", Set.of(UserRole.ROLE_USER));
    private static final CurrentUser STRANGER = new CurrentUser(6L, "s@x.com", Set.of(UserRole.ROLE_USER));

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentProvider gateway = mock(PaymentProvider.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private PaymentServiceImpl service;
    private Payment payment;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties("INR", Duration.ofMinutes(16),
                new PaymentProperties.Razorpay("k", "s", "w", "http://cb"));
        service = new PaymentServiceImpl(repository, gateway, mock(UserClient.class), properties, events,
                new DirectTransactionTemplate(), Clock.systemUTC());

        payment = Payment.builder()
                .id(11L).userId(OWNER.id()).bookingId(21L)
                .amount(new BigDecimal("1234.50")).currency("INR")
                .provider(PaymentGateway.RAZORPAY).status(PaymentStatus.PENDING)
                .transactionId("TXN_1")
                .build();
        when(repository.findByIdForUpdate(11L)).thenReturn(Optional.of(payment));
    }

    @Test
    void capturedMatchingPaymentSettlesAndPublishesOnce() {
        when(gateway.fetchPayment("pay_ABC123")).thenReturn(captured(123450L, "INR"));

        service.verifyPayment(OWNER, verifyRequest("pay_ABC123"));
        service.verifyPayment(OWNER, verifyRequest("pay_ABC123")); // client retry / double click

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_ABC123");
        verify(events, times(1)).publishEvent(any(PaymentCompletedEvent.class));
    }

    @Test
    void underpaymentIsRejected() {
        when(gateway.fetchPayment("pay_ABC123")).thenReturn(captured(100L, "INR")); // paid 1.00 instead of 1234.50

        service.verifyPayment(OWNER, verifyRequest("pay_ABC123"));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(events, never()).publishEvent(any(PaymentCompletedEvent.class));
        verify(events).publishEvent(any(PaymentFailedEvent.class));
    }

    @Test
    void wrongCurrencyIsRejected() {
        when(gateway.fetchPayment("pay_ABC123")).thenReturn(captured(123450L, "USD"));
        service.verifyPayment(OWNER, verifyRequest("pay_ABC123"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    void cannotSettleSomeoneElsesPayment() {
        when(gateway.fetchPayment("pay_ABC123")).thenReturn(captured(123450L, "INR"));

        assertThatThrownBy(() -> service.verifyPayment(STRANGER, verifyRequest("pay_ABC123")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    void authorizedButNotCapturedStaysPending() {
        when(gateway.fetchPayment("pay_ABC123")).thenReturn(new GatewayPayment("pay_ABC123", "authorized", 123450L, "INR", "11"));
        service.verifyPayment(OWNER, verifyRequest("pay_ABC123"));
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void webhookWithInvalidSignatureIsRejected() {
        when(gateway.isValidWebhookSignature("{}", "bad")).thenReturn(false);
        assertThatThrownBy(() -> service.handleRazorpayWebhook("{}", "bad")).isInstanceOf(UnauthorizedException.class);
    }

    private static GatewayPayment captured(long amountMinor, String currency) {
        return new GatewayPayment("pay_ABC123", "captured", amountMinor, currency, "11");
    }

    private static PaymentVerifyRequest verifyRequest(String id) {
        return PaymentVerifyRequest.builder().razorpayPaymentId(id).build();
    }

    /** Runs callbacks inline; transaction boundaries themselves are Spring's responsibility, not under test. */
    private static final class DirectTransactionTemplate extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(mock(TransactionStatus.class));
        }
    }
}
