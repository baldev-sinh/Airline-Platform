package com.baldev.airline.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Published by booking-service when a booking is cancelled or abandoned; consumers release its inventory. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledEvent {

    private Long bookingId;
    private String bookingReference;
    private Long flightInstanceId;
    private List<Long> seatInstanceIds;
    /** True if the booking had been confirmed (inventory was committed, not just held). */
    private boolean wasConfirmed;
    private String reason;
    private Instant cancelledAt;
}
