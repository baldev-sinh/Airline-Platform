package com.baldev.airline.seat.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
