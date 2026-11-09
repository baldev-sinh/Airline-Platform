package com.baldev.airline.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
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

    // ---- For customer notifications (added fields; older consumers ignore them) ----
    private Long userId;
    private String contactEmail;
    private String flightNumber;
    private String departureAirportCode;
    private String arrivalAirportCode;
    private LocalDateTime departureDateTime;
    /** Amount that will be refunded (zero or null if nothing was paid). */
    private BigDecimal refundAmount;
    private String currency;
}
