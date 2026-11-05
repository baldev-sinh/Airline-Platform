package com.baldev.mapper;

import com.baldev.enums.BookingStatus;
import com.baldev.model.Booking;
import com.baldev.model.Passenger;
import com.baldev.payload.dto.PaymentDTO;
import com.baldev.payload.request.BookingRequest;
import com.baldev.payload.response.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BookingMapper {

    public static Booking toEntity(
            BookingRequest request,
            Long userId,
            Set<Passenger> passengers,
            String bookingReference
    ) {
        return Booking.builder()
                .bookingReference(bookingReference)
                .userId(userId)
                .flightId(request.getFlightId())
                .flightInstanceId(request.getFlightInstanceId())
                .fareId(request.getFareId())
                .contactInfo(request.getContactInfo())
                .passengers(passengers)
                .cabinClass(request.getCabinClass())
                .ancillaryIds(request.getAncillaryIds())
                .mealIds(request.getMealIds())
                .status(BookingStatus.PENDING)
                .build();
    }

    public static BookingResponse
    toResponse(Booking booking,
               PaymentDTO paymentDTO,
               FareResponse fareResponse,
               FlightResponse flightResponse,
               FlightInstanceResponse flightInstanceResponse,
               List<FlightCabinAncillaryResponse> ancillaries,
               List<FlightMealResponse> meals,
               List<SeatInstanceResponse> seats) {


        List<PassengerResponse> passengerResponses = booking.getPassengers() != null ?
                booking.getPassengers().stream()
                        .map(PassengerMapper::toResponse)
                        .collect(Collectors.toList()) : null;

        List<TicketResponse> ticketResponses = booking.getTickets() != null ?
                booking.getTickets().stream()
                        .map(TicketMapper::toResponse)
                        .collect(Collectors.toList()) : null;


        return BookingResponse.builder()
                .id(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUserId())

//                flight details
                .flightId(booking.getFlightInstanceId())
                .flightNumber(flightResponse != null ? flightResponse.getFlightNumber() : null)
                .flightName(flightResponse != null && flightResponse.getArrivalAirport() != null && flightResponse.getDepartureAirport() != null
                        ? flightResponse.getArrivalAirport().getCity().getName() + " - " + flightResponse.getDepartureAirport().getCity().getName()
                        : null)
                .departureTime(flightInstanceResponse != null ? flightInstanceResponse.getDepartureDateTime() : null)
                .arrivalTime(flightInstanceResponse != null ? flightInstanceResponse.getArrivalDateTime() : null)
                .flightDuration(flightInstanceResponse != null ? flightInstanceResponse.getFormattedDuration() : null)

//                airport details
                .departureAirport(flightResponse != null && flightResponse.getDepartureAirport() != null ? flightResponse.getDepartureAirport().getDetailedName() : null)
                .arrivalAirport(flightResponse != null && flightResponse.getArrivalAirport() != null ? flightResponse.getArrivalAirport().getName() : null)
                .status(booking.getStatus())
                .bookingDate(booking.getBookingDate())
                .lastModified(booking.getLastModified())
                .passengers(passengerResponses)
                .tickets(ticketResponses)

                .totalPassengers(booking.getPassengers() != null ? booking.getPassengers().size() : 0)

                .ancillaries(ancillaries)
                .meals(meals)
                .seatInstances(seats)
                .paymentStatus(paymentDTO != null ? paymentDTO.getStatus() : null)

//                fare details
                .fareName(fareResponse != null ? fareResponse.getName() : null)
                .fareBaseFare(fareResponse != null ? fareResponse.getBaseFare() : null)
                .fareTaxesAndFees(fareResponse != null ? fareResponse.getTaxesAndFees() : null)
                .fareAirlineFees(fareResponse != null ? fareResponse.getAirlineFees() : null)
                .totalAmount(fareResponse != null ? fareResponse.getTotalPrice() : null)

//                contact information
                .contactInfo(booking.getContactInfo())

                .build();
    }
}
