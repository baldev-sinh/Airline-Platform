package com.baldev.airline.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

/** Published by booking-service once a cancelled booking's money is back with the customer. Keyed by bookingId. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRefundedEvent {
    private Long bookingId;
    private String bookingReference;
    private Long userId;
    private String contactEmail;
    private String flightNumber;
    private String departureAirportCode;
    private String arrivalAirportCode;
    private LocalDateTime departureDateTime;
    private BigDecimal amount;
    private String currency;
    private String providerRefundId;
    private Instant refundedAt;
}
