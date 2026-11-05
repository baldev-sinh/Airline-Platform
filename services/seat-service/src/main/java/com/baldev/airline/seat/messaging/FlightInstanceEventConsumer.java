package com.baldev.airline.seat.messaging;

import com.baldev.airline.common.event.FlightInstanceCreatedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.seat.service.SeatInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Builds the per-flight seat inventory when flight-ops creates a new flight instance. */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightInstanceEventConsumer {

    private final SeatInventoryService seatInventoryService;

    @KafkaListener(topics = KafkaTopics.FLIGHT_INSTANCE_CREATED)
    public void handleFlightInstanceCreated(FlightInstanceCreatedEvent event) {
        log.info("Received FlightInstanceCreated flightInstanceId={} aircraftId={}",
                event.getFlightInstanceId(), event.getAircraftId());
        seatInventoryService.createInventory(event.getFlightId(), event.getFlightInstanceId(), event.getAircraftId());
    }
}
