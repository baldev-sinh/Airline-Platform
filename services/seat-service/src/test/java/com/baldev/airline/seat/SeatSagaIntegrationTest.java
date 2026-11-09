package com.baldev.airline.seat;

import com.baldev.airline.common.enums.SeatAvailabilityStatus;
import com.baldev.airline.common.event.BookingCancelledEvent;
import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.seat.payload.request.SeatHoldRequest;
import com.baldev.airline.seat.model.FlightInstanceCabin;
import com.baldev.airline.seat.model.SeatInstance;
import com.baldev.airline.seat.repository.FlightInstanceCabinRepository;
import com.baldev.airline.seat.repository.SeatInstanceRepository;
import com.baldev.airline.seat.service.SeatInventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The seat side of the saga against real MySQL and Kafka and the real central configuration: seats held at
 * checkout become BOOKED on booking.confirmed and go back on sale on booking.cancelled, exactly once even when
 * Kafka redelivers. Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/seat-service.yaml",
        "eureka.client.enabled=false",
        "airline.security.service-auth.mode=headers"
})
class SeatSagaIntegrationTest {

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

    @Autowired
    private SeatInventoryService seatInventoryService;
    @Autowired
    private SeatInstanceRepository seatInstanceRepository;
    @Autowired
    private FlightInstanceCabinRepository cabinRepository;
    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void heldSeatsAreBookedOnConfirmationAndReleasedOnCancellation() {
        long flightInstanceId = 500L;
        long bookingId = 42L;
        FlightInstanceCabin cabin = cabinRepository.save(FlightInstanceCabin.builder()
                .flightInstanceId(flightInstanceId).totalSeats(2).bookedSeats(0).build());
        List<Long> seats = List.of(seat(flightInstanceId, cabin), seat(flightInstanceId, cabin));
        seatInventoryService.holdSeats(SeatHoldRequest.builder()
                .bookingId(bookingId).flightInstanceId(flightInstanceId).seatInstanceIds(seats).build());
        assertThat(statuses(seats)).containsOnly(SeatAvailabilityStatus.HELD);

        BookingConfirmedEvent confirmed = BookingConfirmedEvent.builder().bookingId(bookingId).bookingReference("ABC123")
                .flightInstanceId(flightInstanceId).seatInstanceIds(seats).build();
        kafkaTemplate.send(KafkaTopics.BOOKING_CONFIRMED, String.valueOf(bookingId), confirmed);
        kafkaTemplate.send(KafkaTopics.BOOKING_CONFIRMED, String.valueOf(bookingId), confirmed); // redelivery

        await().atMost(Duration.ofSeconds(30)).until(() -> statuses(seats).stream().allMatch(s -> s == SeatAvailabilityStatus.BOOKED));
        await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(10))
                .until(() -> cabinRepository.findById(cabin.getId()).orElseThrow().getBookedSeats() == 2);

        kafkaTemplate.send(KafkaTopics.BOOKING_CANCELLED, String.valueOf(bookingId), BookingCancelledEvent.builder()
                .bookingId(bookingId).bookingReference("ABC123").flightInstanceId(flightInstanceId)
                .seatInstanceIds(seats).wasConfirmed(true).reason("Cancelled by customer").cancelledAt(Instant.now())
                .build());

        await().atMost(Duration.ofSeconds(30)).until(() -> statuses(seats).stream().allMatch(s -> s == SeatAvailabilityStatus.AVAILABLE));
        await().atMost(Duration.ofSeconds(10))
                .until(() -> cabinRepository.findById(cabin.getId()).orElseThrow().getBookedSeats() == 0);
    }

    private Long seat(long flightInstanceId, FlightInstanceCabin cabin) {
        return seatInstanceRepository.save(SeatInstance.builder().flightId(1L).flightInstanceId(flightInstanceId)
                .flightInstanceCabin(cabin).status(SeatAvailabilityStatus.AVAILABLE).build()).getId();
    }

    private List<SeatAvailabilityStatus> statuses(List<Long> ids) {
        return seatInstanceRepository.findAllById(ids).stream().map(SeatInstance::getStatus).toList();
    }
}
