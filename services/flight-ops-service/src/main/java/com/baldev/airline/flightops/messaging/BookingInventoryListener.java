package com.baldev.airline.flightops.messaging;

import com.baldev.airline.common.event.BookingCancelledEvent;
import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.flightops.model.ProcessedInventoryEvent;
import com.baldev.airline.flightops.repository.FlightInstanceRepository;
import com.baldev.airline.flightops.repository.ProcessedInventoryEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Keeps {@code FlightInstance.availableSeats} (used by flight search) in step with confirmed bookings.
 * The idempotency row and the seat-count update commit in one transaction, so each event applies exactly once.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingInventoryListener {

    private final FlightInstanceRepository flightInstanceRepository;
    private final ProcessedInventoryEventRepository processedRepository;

    @Transactional
    @KafkaListener(topics = KafkaTopics.BOOKING_CONFIRMED)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        int seats = event.getSeatInstanceIds() != null ? event.getSeatInstanceIds().size() : 0;
        if (seats == 0 || !markProcessed("CONFIRMED:" + event.getBookingId())) {
            return;
        }
        if (flightInstanceRepository.decrementAvailableSeats(event.getFlightInstanceId(), seats) == 0) {
            log.error("ACTION REQUIRED: oversold flightInstanceId={} by booking {} ({} seats)",
                    event.getFlightInstanceId(), event.getBookingId(), seats);
        }
    }

    @Transactional
    @KafkaListener(topics = KafkaTopics.BOOKING_CANCELLED)
    public void onBookingCancelled(BookingCancelledEvent event) {
        // Only confirmed bookings consumed inventory here; cancelled holds never did.
        int seats = event.getSeatInstanceIds() != null ? event.getSeatInstanceIds().size() : 0;
        if (!event.isWasConfirmed() || seats == 0 || !markProcessed("CANCELLED:" + event.getBookingId())) {
            return;
        }
        flightInstanceRepository.incrementAvailableSeats(event.getFlightInstanceId(), seats);
    }

    /** @return true if this event key was not seen before (and is now recorded). */
    private boolean markProcessed(String key) {
        if (processedRepository.existsById(key)) {
            log.info("Skipping already-processed inventory event {}", key);
            return false;
        }
        processedRepository.save(new ProcessedInventoryEvent(key, Instant.now()));
        return true;
    }
}
