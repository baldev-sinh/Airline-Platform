package com.baldev.airline.payment;

import com.baldev.airline.common.enums.PaymentGateway;
import com.baldev.airline.common.enums.PaymentStatus;
import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.event.RefundRequestedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.payment.client.dto.UserDTO;
import com.baldev.airline.payment.payload.request.PaymentInitiateRequest;
import com.baldev.airline.payment.payload.request.PaymentVerifyRequest;
import com.baldev.airline.payment.payload.response.PaymentInitiateResponse;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.payment.client.UserClient;
import com.baldev.airline.payment.model.Refund;
import com.baldev.airline.payment.repository.PaymentRepository;
import com.baldev.airline.payment.repository.RefundRepository;
import com.baldev.airline.payment.service.PaymentService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The payment side of the saga against real MySQL and Kafka, the real central configuration and the offline
 * payment provider (profile payment-stub): a verified payment is announced exactly once on payment.completed
 * through the outbox, and a refund.requested (as booking-service sends it, twice) refunds once and announces
 * payment.refunded. Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
// Close the context before the containers stop, so background pollers do not log connection errors.
@DirtiesContext
@ActiveProfiles("payment-stub")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/payment-service.yaml,"
                + "optional:file:../../config-repo/payment-service-payment-stub.yaml",
        "eureka.client.enabled=false",
        "airline.outbox.poll-interval=100ms",
        "airline.payment.reconciliation.enabled=false"
})
class PaymentSagaIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.2.0");

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    private static final CurrentUser PAYER = new CurrentUser(5L, "asha@example.com", Set.of(UserRole.ROLE_USER));
    private static KafkaConsumer<String, String> events;
    private static final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @MockitoBean
    private UserClient userClient;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private RefundRepository refundRepository;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeAll
    static void subscribe() {
        events = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "saga-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"),
                new StringDeserializer(), new StringDeserializer());
        events.subscribe(List.of(KafkaTopics.PAYMENT_COMPLETED, KafkaTopics.PAYMENT_REFUNDED));
    }

    @AfterAll
    static void close() {
        events.close();
    }

    @Test
    void verifiedPaymentIsAnnouncedOnceAndRefundedOnce() {
        UserDTO user = new UserDTO();
        user.setId(5L);
        user.setFullName("Asha Rao");
        user.setEmail("asha@example.com");
        when(userClient.getUserById(anyLong())).thenReturn(user);

        PaymentInitiateResponse checkout = paymentService.initiatePayment(PaymentInitiateRequest.builder()
                .userId(5L).bookingId(42L).amount(new BigDecimal("9300.00")).currency("INR")
                .gateway(PaymentGateway.RAZORPAY).description("Booking ABC123").build());
        Long paymentId = checkout.getPaymentId();

        PaymentVerifyRequest verify = new PaymentVerifyRequest();
        verify.setRazorpayPaymentId("pay_STUBOK" + paymentId);
        paymentService.verifyPayment(PAYER, verify);
        paymentService.verifyPayment(PAYER, verify); // browser retry: settles once

        assertThat(awaitEvent(KafkaTopics.PAYMENT_COMPLETED, 42L).value()).contains("\"amount\":9300.00");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.SUCCESS);

        RefundRequestedEvent refund = RefundRequestedEvent.builder().refundKey("booking-42-cancel").bookingId(42L)
                .paymentId(paymentId).userId(5L).amount(new BigDecimal("9300.00")).currency("INR")
                .reason("Customer cancellation").requestedAt(Instant.now()).build();
        kafkaTemplate.send(KafkaTopics.REFUND_REQUESTED, "42", refund);
        kafkaTemplate.send(KafkaTopics.REFUND_REQUESTED, "42", refund); // redelivery

        assertThat(awaitEvent(KafkaTopics.PAYMENT_REFUNDED, 42L).value()).contains("rfnd_STUB");
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(paymentRepository.findById(paymentId).orElseThrow().getStatus()).isEqualTo(PaymentStatus.REFUNDED));
        assertThat(refundRepository.findAll()).singleElement()
                .satisfies(r -> assertThat(r.getStatus()).isEqualTo(Refund.State.PROCESSED));

        // Give the redelivered request time to be consumed, then make sure nothing was announced twice.
        await().pollDelay(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(10)).until(() -> {
            events.poll(Duration.ofMillis(200)).forEach(received::add);
            return true;
        });
        assertThat(received.stream().filter(r -> r.topic().equals(KafkaTopics.PAYMENT_COMPLETED))).hasSize(1);
        assertThat(received.stream().filter(r -> r.topic().equals(KafkaTopics.PAYMENT_REFUNDED))).hasSize(1);
    }

    private static ConsumerRecord<String, String> awaitEvent(String topic, Long bookingId) {
        return await().atMost(Duration.ofSeconds(30)).until(() -> {
            events.poll(Duration.ofMillis(200)).forEach(received::add);
            return received.stream()
                    .filter(r -> r.topic().equals(topic) && r.key().equals(String.valueOf(bookingId)))
                    .findFirst().orElse(null);
        }, r -> r != null);
    }
}
