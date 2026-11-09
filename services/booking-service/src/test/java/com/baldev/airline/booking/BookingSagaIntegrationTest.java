package com.baldev.airline.booking;

import com.baldev.airline.booking.client.AirlineClient;
import com.baldev.airline.booking.client.AncillaryClient;
import com.baldev.airline.booking.client.FlightClient;
import com.baldev.airline.booking.client.PaymentClient;
import com.baldev.airline.booking.client.PricingClient;
import com.baldev.airline.booking.client.SeatClient;
import com.baldev.airline.booking.model.Booking;
import com.baldev.airline.booking.model.Ticket;
import com.baldev.airline.booking.repository.BookingRepository;
import com.baldev.airline.booking.service.BookingService;
import com.baldev.airline.common.embeddable.ContactInfo;
import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.common.enums.CabinClassType;
import com.baldev.airline.common.enums.FlightStatus;
import com.baldev.airline.common.enums.Gender;
import com.baldev.airline.common.enums.RefundStatus;
import com.baldev.airline.common.enums.TicketStatus;
import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentRefundedEvent;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.booking.payload.request.BookingRequest;
import com.baldev.airline.booking.payload.request.PassengerRequest;
import com.baldev.airline.booking.client.dto.AirportResponse;
import com.baldev.airline.booking.client.dto.FareResponse;
import com.baldev.airline.booking.client.dto.FlightInstanceResponse;
import com.baldev.airline.booking.client.dto.PaymentInitiateResponse;
import com.baldev.airline.booking.client.dto.PriceQuoteResponse;
import com.baldev.airline.booking.client.dto.SeatHoldResponse;
import com.baldev.airline.common.web.CurrentUser;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

/**
 * The booking side of the checkout saga against real MySQL and Kafka, with the real central configuration
 * (config-repo) and the transactional outbox. Other services are mocked at their HTTP boundary; their side of
 * the saga is covered by the payment-service and seat-service integration tests, which consume and produce
 * the same event contracts.
 *
 * <pre>
 *   create (PENDING) -> payment.completed -> CONFIRMED + tickets -> booking.confirmed
 *   cancel           -> booking.cancelled (wasConfirmed) + refund.requested
 *   payment.refunded -> REFUNDED tickets -> booking.refunded
 * </pre>
 *
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
// Close the context before the containers stop, so background pollers do not log connection errors.
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/booking-service.yaml",
        "eureka.client.enabled=false",
        "airline.security.service-auth.mode=headers",
        "airline.outbox.poll-interval=100ms"
})
class BookingSagaIntegrationTest {

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

    private static final CurrentUser CUSTOMER = new CurrentUser(5L, "asha@example.com", Set.of(UserRole.ROLE_USER));
    private static KafkaConsumer<String, String> events;
    private static final List<ConsumerRecord<String, String>> received = new ArrayList<>();

    @MockitoBean
    private FlightClient flightClient;
    @MockitoBean
    private PricingClient pricingClient;
    @MockitoBean
    private SeatClient seatClient;
    @MockitoBean
    private AncillaryClient ancillaryClient;
    @MockitoBean
    private PaymentClient paymentClient;
    @MockitoBean
    private AirlineClient airlineClient;

    @Autowired
    private BookingService bookingService;
    @Autowired
    private BookingRepository bookingRepository;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeAll
    static void subscribe() {
        events = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "saga-test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed"),
                new StringDeserializer(), new StringDeserializer());
        events.subscribe(List.of(KafkaTopics.BOOKING_CONFIRMED, KafkaTopics.BOOKING_CANCELLED,
                KafkaTopics.REFUND_REQUESTED, KafkaTopics.BOOKING_REFUNDED));
    }

    @AfterAll
    static void close() {
        events.close();
    }

    @Test
    void paidBookingIsTicketedThenCancelledAndRefunded() {
        stubCheckout();

        // 1. Checkout: PENDING booking priced server-side (2 passengers x 4500.00 fare + 300.00 seats).
        PaymentInitiateResponse checkout = bookingService.createBooking(CUSTOMER, bookingRequest());
        Booking pending = bookingRepository.findAll().getFirst();
        assertThat(checkout.getPaymentId()).isEqualTo(77L);
        assertThat(pending.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(pending.getTotalAmount()).isEqualByComparingTo("9300.00");
        Long bookingId = pending.getId();

        // 2. payment.completed (as payment-service publishes it) confirms and tickets the booking.
        PaymentCompletedEvent paid = PaymentCompletedEvent.builder().paymentId(77L).bookingId(bookingId).userId(5L)
                .amount(new BigDecimal("9300.00")).currency("INR").gateway("RAZORPAY").transactionId("TXN_1")
                .providerPaymentId("pay_1").paidAt(Instant.now()).build();
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED, String.valueOf(bookingId), paid);
        kafkaTemplate.send(KafkaTopics.PAYMENT_COMPLETED, String.valueOf(bookingId), paid); // redelivery

        await().atMost(Duration.ofSeconds(30)).until(() -> status(bookingId) == BookingStatus.CONFIRMED);
        assertThat(tickets(bookingId)).hasSize(2).allMatch(t -> t.getStatus() == TicketStatus.BOOKED);
        assertThat(awaitEvent(KafkaTopics.BOOKING_CONFIRMED, bookingId).value()).contains("\"bookingReference\"");

        // 3. Customer cancels: fare has no rules, so everything is refunded; seats are released by consumers.
        when(pricingClient.getFareRulesByFareId(anyLong())).thenThrow(ResourceNotFoundException.of("Fare rules", 9L));
        bookingService.cancelBooking(CUSTOMER, bookingId);

        assertThat(awaitEvent(KafkaTopics.BOOKING_CANCELLED, bookingId).value()).contains("\"wasConfirmed\":true");
        assertThat(awaitEvent(KafkaTopics.REFUND_REQUESTED, bookingId).value())
                .contains("\"refundKey\":\"booking-" + bookingId + "-cancel\"").contains("9300.00");
        assertThat(refundStatus(bookingId)).isEqualTo(RefundStatus.PENDING);

        // 4. payment.refunded closes the loop: tickets refunded and the customer is told (booking.refunded).
        kafkaTemplate.send(KafkaTopics.PAYMENT_REFUNDED, String.valueOf(bookingId), PaymentRefundedEvent.builder()
                .refundKey("booking-" + bookingId + "-cancel").paymentId(77L).bookingId(bookingId).userId(5L)
                .amount(new BigDecimal("9300.00")).currency("INR").providerRefundId("rfnd_1").refundedAt(Instant.now())
                .build());

        await().atMost(Duration.ofSeconds(30)).until(() -> refundStatus(bookingId) == RefundStatus.REFUNDED);
        assertThat(tickets(bookingId)).allMatch(t -> t.getStatus() == TicketStatus.REFUNDED);
        assertThat(awaitEvent(KafkaTopics.BOOKING_REFUNDED, bookingId).value()).contains("rfnd_1");
        // Exactly one confirmation despite the redelivered payment event.
        assertThat(received.stream().filter(r -> r.topic().equals(KafkaTopics.BOOKING_CONFIRMED)
                && r.key().equals(String.valueOf(bookingId)))).hasSize(1);
    }

    // ------------------------------------------------------------------------------------------------------

    private void stubCheckout() {
        AirportResponse bom = AirportResponse.builder().id(1L).iataCode("BOM").build();
        AirportResponse del = AirportResponse.builder().id(2L).iataCode("DEL").build();
        FlightInstanceResponse instance = FlightInstanceResponse.builder()
                .id(11L).flightId(1L).flightNumber("BA101").airlineId(10L).airlineName("Baldev Airways")
                .departureAirport(bom).arrivalAirport(del).isActive(true).status(FlightStatus.SCHEDULED)
                .departureDateTime(LocalDateTime.now(ZoneOffset.UTC).plusDays(20))
                .arrivalDateTime(LocalDateTime.now(ZoneOffset.UTC).plusDays(20).plusHours(2))
                .build();
        when(flightClient.getFlightInstanceById(11L)).thenReturn(instance);
        FareResponse fare = new FareResponse();
        fare.setId(9L);
        fare.setFlightId(1L);
        fare.setCabinClassId(3L);
        fare.setCabinClass(CabinClassType.ECONOMY);
        fare.setName("Saver");
        fare.setTotalPrice(new BigDecimal("4500.00"));
        when(pricingClient.getFareById(9L)).thenReturn(fare);
        when(seatClient.holdSeats(any())).thenAnswer(inv -> SeatHoldResponse.builder()
                .totalPrice(new BigDecimal("300.00")).expiresAt(Instant.now().plus(Duration.ofMinutes(20))).build());
        when(ancillaryClient.quoteAncillaries(any())).thenReturn(PriceQuoteResponse.builder().total(BigDecimal.ZERO).build());
        when(ancillaryClient.quoteMeals(any())).thenReturn(PriceQuoteResponse.builder().total(BigDecimal.ZERO).build());
        PaymentInitiateResponse payment = new PaymentInitiateResponse();
        payment.setPaymentId(77L);
        payment.setCheckoutUrl("https://rzp.example/checkout");
        when(paymentClient.initiatePayment(any())).thenReturn(payment);
    }

    private static BookingRequest bookingRequest() {
        return BookingRequest.builder()
                .flightId(1L).flightInstanceId(11L).cabinClass(CabinClassType.ECONOMY).fareId(9L)
                .contactInfo(ContactInfo.builder().email("asha@example.com").phone("+919800000000").build())
                .passengers(List.of(passenger("Asha", 101L), passenger("Ravi", 102L)))
                .build();
    }

    private static PassengerRequest passenger(String firstName, Long seat) {
        return PassengerRequest.builder().firstName(firstName).lastName("Rao").email("asha@example.com")
                .phone("+919800000000").dateOfBirth(LocalDate.of(1990, 1, 1)).gender(Gender.FEMALE)
                .seatInstanceId(seat).nationality("IN").build();
    }

    private BookingStatus status(Long id) {
        return bookingRepository.findById(id).orElseThrow().getStatus();
    }

    private RefundStatus refundStatus(Long id) {
        return bookingRepository.findById(id).orElseThrow().getRefundStatus();
    }

    private List<Ticket> tickets(Long id) {
        return transactionTemplate.execute(s -> List.copyOf(bookingRepository.findWithDetailsById(id).orElseThrow().getTickets()));
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
