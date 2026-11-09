package com.baldev.airline.notification;

import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.event.PassengerNotificationData;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.notification.model.Notification;
import com.baldev.airline.notification.model.Notification.Channel;
import com.baldev.airline.notification.model.Notification.Status;
import com.baldev.airline.notification.repository.NotificationRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Kafka event in, e-mail and inbox entry out, against real MySQL and Kafka and the real central configuration
 * (config-repo). Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
// Close the context before the containers stop, so background pollers do not log connection errors.
@DirtiesContext
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/notification-service.yaml",
        "eureka.client.enabled=false",
        // This test calls the API with gateway identity headers; JWT verification is covered in common-lib.
        "airline.security.service-auth.mode=headers",
        "SMTP_HOST=localhost",
        "NOTIFICATION_FROM=no-reply@test.local",
        "PUBLIC_BASE_URL=https://fly.example"
})
@AutoConfigureMockMvc
class NotificationFlowIntegrationTest {

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

    @MockitoBean
    private JavaMailSender mailSender;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private NotificationRepository repository;
    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void mail() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
    }

    @Test
    void bookingConfirmationIsEmailedOnceAndShownInTheInbox() throws Exception {
        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .bookingId(42L).bookingReference("ABC123").userId(5L).contactEmail("asha@example.com")
                .flightNumber("BA101").airlineName("Baldev Airways").cabinClass("ECONOMY")
                .departureAirportCode("BOM").departureCity("Mumbai").departureDateTime(LocalDateTime.of(2026, 12, 1, 6, 30))
                .arrivalAirportCode("DEL").arrivalCity("Delhi").arrivalDateTime(LocalDateTime.of(2026, 12, 1, 8, 40))
                .passengers(List.of(PassengerNotificationData.builder().firstName("Asha").lastName("Rao")
                        .ticketNumber("9990000000001").build()))
                .totalAmount(new BigDecimal("4999.00")).currency("INR")
                .build();

        // Delivered twice, as Kafka may do: still one e-mail and one inbox entry.
        kafkaTemplate.send(KafkaTopics.BOOKING_CONFIRMED, "42", event).get();
        kafkaTemplate.send(KafkaTopics.BOOKING_CONFIRMED, "42", event).get();

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            List<Notification> all = repository.findAll().stream()
                    .filter(n -> n.getEventKey().equals("booking.confirmed:42")).toList();
            assertThat(all).extracting(Notification::getChannel).containsExactlyInAnyOrder(Channel.EMAIL, Channel.IN_APP);
            assertThat(all).allMatch(n -> n.getStatus() == Status.SENT);
        });

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, atLeastOnce()).send(sent.capture());
        assertThat(sent.getAllValues()).hasSize(1);
        assertThat(sent.getValue().getSubject()).contains("ABC123");
        Notification email = repository.findAll().stream()
                .filter(n -> n.getChannel() == Channel.EMAIL && n.getEventKey().equals("booking.confirmed:42"))
                .findFirst().orElseThrow();
        assertThat(email.getBody()).contains("9990000000001").contains("https://fly.example/trips/42");

        mvc.perform(get("/api/notifications").header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].subject").value(org.hamcrest.Matchers.containsString("ABC123")))
                .andExpect(jsonPath("$.content[0].recipient").doesNotExist());
        mvc.perform(get("/api/notifications").header("X-User-Id", "6"))
                .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/notifications/unread-count").header("X-User-Id", "5"))
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void smtpOutageKeepsTheEmailForRetry() {
        when(mailSender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(MimeMessage.class));
        BookingConfirmedEvent event = BookingConfirmedEvent.builder().bookingId(77L).bookingReference("XYZ789")
                .userId(9L).contactEmail("b@example.com").flightNumber("BA202").departureAirportCode("DEL")
                .arrivalAirportCode("BOM").totalAmount(BigDecimal.TEN).currency("INR").build();

        kafkaTemplate.send(KafkaTopics.BOOKING_CONFIRMED, "77", event);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertThat(repository.findAll())
                .filteredOn(n -> n.getEventKey().equals("booking.confirmed:77") && n.getChannel() == Channel.EMAIL)
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.getStatus()).isEqualTo(Status.PENDING);
                    assertThat(n.getAttempts()).isEqualTo(1);
                    assertThat(n.getLastError()).contains("SMTP down");
                    assertThat(n.getNextAttemptAt()).isNotNull();
                }));
    }
}
