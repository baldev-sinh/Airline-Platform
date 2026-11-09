package com.baldev.airline.booking.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Client-side copy of seat-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldResponse {

    private Long bookingId;
    private List<Long> seatInstanceIds;
    /** Sum of seat surcharges for the held seats. */
    private BigDecimal totalPrice;
    /** Seats are released automatically after this instant unless the booking is confirmed. */
    private Instant expiresAt;
}
