package com.baldev.airline.seat.messaging;

import com.baldev.airline.common.event.BookingCancelledEvent;
import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.seat.service.SeatInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Moves seats through their lifecycle as bookings are confirmed or cancelled. */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventListener {

    private final SeatInventoryService seatInventoryService;

    @KafkaListener(topics = KafkaTopics.BOOKING_CONFIRMED)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Received BookingConfirmed bookingId={}", event.getBookingId());
        seatInventoryService.confirmSeats(event.getBookingId());
    }

    @KafkaListener(topics = KafkaTopics.BOOKING_CANCELLED)
    public void onBookingCancelled(BookingCancelledEvent event) {
        log.info("Received BookingCancelled bookingId={}", event.getBookingId());
        seatInventoryService.releaseSeats(event.getBookingId(), event.isWasConfirmed());
    }
}
