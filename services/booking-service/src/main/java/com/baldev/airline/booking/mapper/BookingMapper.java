package com.baldev.airline.booking.mapper;

import com.baldev.airline.booking.model.Booking;
import com.baldev.airline.booking.payload.response.BookingResponse;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

public final class BookingMapper {

    private BookingMapper() {
    }

    /** Built entirely from the booking's own snapshot: no remote calls on the read path. */
    public static BookingResponse toResponse(Booking booking) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime departure = booking.getDepartureDateTime();
        String route = booking.getDepartureCity() != null && booking.getArrivalCity() != null
                ? booking.getDepartureCity() + " - " + booking.getArrivalCity()
                : null;

        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUserId())
                .flightId(booking.getFlightId())
                .flightNumber(booking.getFlightNumber())
                .flightName(route)
                .departureAirport(booking.getDepartureAirportCode())
                .arrivalAirport(booking.getArrivalAirportCode())
                .departureTime(departure)
                .arrivalTime(booking.getArrivalDateTime())
                .status(booking.getStatus())
                .bookingDate(booking.getBookingDate())
                .lastModified(booking.getLastModified())
                .passengers(booking.getPassengers().stream()
                        .sorted(Comparator.comparing(p -> p.getId() != null ? p.getId() : 0L))
                        .map(PassengerMapper::toResponse)
                        .toList())
                .tickets(booking.getTickets().stream().map(TicketMapper::toResponse).toList())
                .totalPassengers(booking.getPassengers().size())
                .seatInstanceIds(List.copyOf(booking.getSeatInstanceIds()))
                .fareId(booking.getFareId())
                .fareName(booking.getFareName())
                .currency(booking.getCurrency())
                .fareAmount(booking.getFareAmount())
                .seatAmount(booking.getSeatAmount())
                .ancillaryAmount(booking.getAncillaryAmount())
                .mealAmount(booking.getMealAmount())
                .totalAmount(booking.getTotalAmount())
                .paymentId(booking.getPaymentId())
                .cancellationReason(booking.getCancellationReason())
                .refundStatus(booking.getRefundStatus())
                .refundAmount(booking.getRefundAmount())
                .refundedAt(booking.getRefundedAt())
                .isUpcoming(departure != null && departure.isAfter(now))
                .isPast(departure != null && !departure.isAfter(now))
                .contactInfo(booking.getContactInfo())
                .build();
    }
}
