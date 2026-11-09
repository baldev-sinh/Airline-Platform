package com.baldev.airline.booking.payload.response;

import java.math.BigDecimal;

import com.baldev.airline.common.enums.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TicketResponse {
    private Long id;

    private String ticketNumber;
    private TicketStatus status;
    private LocalDateTime issuedAt;

    // Booking details
    private Long bookingId;
    private String bookingReference;

    // Passenger details
    private Long passengerId;
    private String passengerFirstName;
    private String passengerLastName;
    private String passengerEmail;

    // Payment details
    private Long paymentId;
    private BigDecimal paymentAmount;

}
