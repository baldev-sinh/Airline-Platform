package com.baldev.airline.booking.payload.response;

import com.baldev.airline.booking.client.dto.FlightCabinAncillaryResponse;
import com.baldev.airline.booking.client.dto.FlightMealResponse;
import com.baldev.airline.booking.client.dto.PaymentLinkResponse;
import com.baldev.airline.booking.client.dto.SeatInstanceResponse;

import java.math.BigDecimal;

import com.baldev.airline.common.embeddable.ContactInfo;
import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.common.enums.RefundStatus;
import com.baldev.airline.common.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class BookingResponse {

    private Long id;
    private String bookingReference;

    private Long userId;
    private String userName;
    private String userEmail;

    private Long flightId;
    private String flightNumber;
    private String flightName;
    private String departureAirport;
    private String arrivalAirport;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;

    private BookingStatus status;
    private Instant bookingDate;
    private Instant lastModified;

    private List<PassengerResponse> passengers;
    private List<SeatInstanceResponse> seatInstances;
    private PaymentLinkResponse payment;
    private List<FlightCabinAncillaryResponse> ancillaries;
    private List<FlightMealResponse> meals;
    private List<TicketResponse> tickets;

    // Payment details
    private PaymentStatus paymentStatus;
    private String paymentLink;

    private Long fareId;
    private String fareName;
    private BigDecimal fareBaseFare;
    private BigDecimal fareTaxesAndFees;
    private BigDecimal fareAirlineFees;

    private Integer totalPassengers;
    private BigDecimal totalAmount;

    // Flight duration and other derived information
    private String flightDuration;
    private Boolean isUpcoming;
    private Boolean isPast;

    private String currency;
    private BigDecimal fareAmount;
    private BigDecimal seatAmount;
    private BigDecimal ancillaryAmount;
    private BigDecimal mealAmount;
    private List<Long> seatInstanceIds;
    private Long paymentId;
    private String cancellationReason;
    private RefundStatus refundStatus;
    private BigDecimal refundAmount;
    private Instant refundedAt;
    private ContactInfo contactInfo;
}
