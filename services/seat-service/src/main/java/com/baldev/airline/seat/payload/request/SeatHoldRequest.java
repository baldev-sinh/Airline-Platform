package com.baldev.airline.seat.payload.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Internal: booking-service asks seat-service to hold seats for a booking during checkout. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatHoldRequest {

    @NotNull
    private Long bookingId;

    @NotNull
    private Long flightInstanceId;

    @NotEmpty
    private List<@NotNull Long> seatInstanceIds;
}
