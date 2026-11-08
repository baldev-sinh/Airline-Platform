package com.baldev.airline.payment.service.gateway;

import com.baldev.airline.payment.config.PaymentProperties;
import com.baldev.airline.payment.model.Payment;
import com.baldev.airline.payment.repository.PaymentRepository;
import com.baldev.airline.payment.service.gateway.PaymentProvider.GatewayPayment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StubPaymentGatewayTest {

    private static final String SECRET = "test-webhook-secret";
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private StubPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties("INR", Duration.ofMinutes(16),
                new PaymentProperties.Razorpay("k", "s", SECRET, "http://cb"));
        gateway = new StubPaymentGateway(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.findById(7L)).thenReturn(Optional.of(Payment.builder()
                .id(7L).bookingId(3L).amount(new BigDecimal("6300.00")).currency("INR")
                .createdAt(NOW.minus(Duration.ofMinutes(10))).build()));
    }

    @Test
    void createsOfflineLinkWithPredictableIds() {
        var link = gateway.createPaymentLink(Payment.builder().id(7L).bookingId(3L)
                .amount(new BigDecimal("6300.00")).currency("INR").build(), null);
        assertThat(link.id()).isEqualTo("plink_STUB7");
        assertThat(link.url()).contains("razorpay_payment_id=pay_STUBOK7");
    }

    @Test
    void stubIdsMapToProviderOutcomes() {
        assertThat(gateway.fetchPayment("pay_STUBOK7"))
                .isEqualTo(new GatewayPayment("pay_STUBOK7", "captured", 630000L, "INR", "7"));
        assertThat(gateway.fetchPayment("pay_STUBLOW7").amountMinor()).isEqualTo(629900L);
        assertThat(gateway.fetchPayment("pay_STUBFAIL7").status()).isEqualTo("failed");
        assertThat(gateway.fetchPayment("pay_STUBWAIT7").isCaptured()).isFalse();
    }

    @Test
    void unknownPaymentsAreNotLinkedToABooking() {
        assertThat(gateway.fetchPayment("pay_SomethingElse").notesPaymentId()).isNull();
        assertThat(gateway.fetchPayment("pay_STUBOK999").notesPaymentId()).isNull();
    }

    @Test
    void stubPaymentsBelongToTheirOwnLinkOnly() {
        assertThat(gateway.isPaymentOfLink("plink_STUB7", "pay_STUBOK7")).isTrue();
        assertThat(gateway.isPaymentOfLink("plink_STUB8", "pay_STUBOK7")).isFalse();
        assertThat(gateway.isPaymentOfLink("plink_STUB7", "pay_SomethingElse")).isFalse();
    }

    @Test
    void stubLinksExpireAfterTheLinkExpiry() {
        // Payment 7 was created 10 minutes ago and links last 16: still open.
        assertThat(gateway.fetchPaymentLink("plink_STUB7").status()).isEqualTo("created");

        StubPaymentGateway later = new StubPaymentGateway(repository,
                new PaymentProperties("INR", Duration.ofMinutes(16), new PaymentProperties.Razorpay("k", "s", SECRET, "http://cb")),
                Clock.fixed(NOW.plus(Duration.ofMinutes(7)), ZoneOffset.UTC));
        assertThat(later.fetchPaymentLink("plink_STUB7").isClosed()).isTrue();
    }

    @Test
    void webhookSignatureIsCheckedForReal() throws Exception {
        String body = "{\"event\":\"payment_link.paid\"}";
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));

        assertThat(gateway.isValidWebhookSignature(body, signature)).isTrue();
        assertThat(gateway.isValidWebhookSignature(body + " ", signature)).isFalse();
        assertThat(gateway.isValidWebhookSignature(body, null)).isFalse();
    }
}
