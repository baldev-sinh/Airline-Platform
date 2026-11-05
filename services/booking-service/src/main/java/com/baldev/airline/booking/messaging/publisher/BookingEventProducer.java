package com.baldev.airline.booking.messaging.publisher;

import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.event.PassengerNotificationData;
import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.booking.model.Booking;
import com.baldev.airline.booking.model.Ticket;
import com.baldev.airline.common.payload.dto.UserDTO;
import com.baldev.airline.common.payload.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class BookingEventProducer {

    private final KafkaTemplate<String, BookingConfirmedEvent> kafkaTemplate;


    public void sendBookingConfirmed(Booking booking,
                                     PaymentCompletedEvent payment,
                                     FlightInstanceResponse flight,
                                     FareResponse fare,
                                     UserDTO user) {


        Map<Long, String> ticketByPassenger = booking.getTickets().stream()
                .filter(t -> t.getPassenger() != null)
                .collect(Collectors.toMap(
                        t -> t.getPassenger().getId(), Ticket::getTicketNumber,
                        (a, b) -> a
                ));

        List<PassengerNotificationData> passengers = booking.getPassengers()
                .stream()
                .map(p -> PassengerNotificationData.builder()
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .ticketNumber(ticketByPassenger.getOrDefault(p.getId(), "N/A"))
                        .passportNumber(null)
                        .nationality(p.getNationality())
                        .gender(p.getGender() != null ? p.getGender().name() : "")
                        .adult(p.isAdult())
                        .build()
                ).collect(Collectors.toList());

        String contactEmail = booking.getContactInfo() != null
                ? booking.getContactInfo().getEmail() : null;
        String contactPhone = booking.getContactInfo() != null
                ? booking.getContactInfo().getPhone() : null;


        // ── Flight Details ─────────────────────────────────────────────────────
        String flightNumber = flight != null ? flight.getFlightNumber() : "N/A";
        String airlineName = flight != null ? flight.getAirlineName() : "N/A";
        String airlineLogo = flight != null ? flight.getAirlineLogo() : null;
        String aircraftModel = flight != null ? flight.getAircraftModal() : null;
        String duration = flight != null ? flight.getFormattedDuration() : null;
        LocalDateTime depTime = flight != null ? flight.getDepartureDateTime() : null;
        LocalDateTime arrTime = flight != null ? flight.getArrivalDateTime() : null;

        AirportResponse dep = flight != null ? flight.getDepartureAirport() : null;
        CityResponse depCity = dep != null ? dep.getCity() : null;
        String depCode = dep != null ? dep.getIataCode() : "N/A";
        String depName = dep != null ? dep.getName() : "N/A";
        String depCityName = depCity != null ? depCity.getName() : "N/A";
        String depCountry = depCity != null ? depCity.getCountryName() : "N/A";

        AirportResponse arr = flight != null ? flight.getArrivalAirport() : null;
        CityResponse arrCity = arr != null ? arr.getCity() : null;
        String arrCode = arr != null ? arr.getIataCode() : "N/A";
        String arrName = arr != null ? arr.getName() : "N/A";
        String arrCityName = arrCity != null ? arrCity.getName() : "N/A";
        String arrCountry = arrCity != null ? arrCity.getCountryName() : "N/A";

        // ── Fare & Baggage ────────────────────────────────────────────────────
        String fareName = fare != null ? fare.getName() : null;
        Double baseFare = fare != null ? fare.getBaseFare() : null;
        Double taxes = fare != null ? fare.getTaxesAndFees() : null;
        BaggagePolicyResponse bag = fare != null ? fare.getBaggagePolicy() : null;
        Integer ciPieces = bag != null ? bag.getCheckInBaggagePieces() : null;
        Double ciWeightPer = bag != null ? bag.getCheckInBaggageWeightPerPiece() : null;
        Integer cbPieces = bag != null ? bag.getCabinBaggagePieces() : null;
        Double cbWeightPer = bag != null ? bag.getCabinBaggageWeightPerPiece() : null;


        BookingConfirmedEvent event = BookingConfirmedEvent.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .confirmedAt(LocalDateTime.now())
                .bookingDate(LocalDateTime.ofInstant(booking.getBookingDate(), ZoneId.systemDefault()))
                .cabinClass(booking.getCabinClass() != null ? booking.getCabinClass().name() : "ECONOMY")

                .flexibleTicket(booking.isFlexibleTicket())
                // Contact
                .userId(booking.getUserId())
                .userName(user != null ? user.getFullName() : "Valued Customer")
                .contactEmail(contactEmail)
                .contactPhone(contactPhone)
                // Passengers
                .passengers(passengers)
                // Flight
                .flightInstanceId(booking.getFlightInstanceId())
                .flightNumber(flightNumber)
                .airlineName(airlineName)
                .airlineLogo(airlineLogo)
                .aircraftModel(aircraftModel)
                .departureAirportCode(depCode)
                .departureAirportName(depName)
                .departureCity(depCityName)
                .departureCountry(depCountry)

                .departureDateTime(depTime)
                .arrivalAirportCode(arrCode)
                .arrivalAirportName(arrName)
                .arrivalCity(arrCityName)
                .arrivalCountry(arrCountry)
                .arrivalDateTime(arrTime)
                .flightDuration(duration)
                // Payment
                .totalAmount(payment.getAmount())
                .currency("INR")
                .transactionId(payment.getTransactionId())
                .providerPaymentId(payment.getProviderPaymentId())
                .paymentGateway("RAZORPAY")
                .paidAt(payment.getPaidAt())
                // Fare breakdown
                .fareName(fareName)
                .baseFare(baseFare)
                .taxesAndFees(taxes)
                // Baggage
                .checkinBaggagePieces(ciPieces)
                .checkinBaggageWeightPerPiece(ciWeightPer)
                .cabinBaggagePieces(cbPieces)
                .cabinBaggageWeightPerPiece(cbWeightPer)
                // Policies
                .freeDateChange(fare != null ? fare.getFreeDateChange() : null)
                .partialRefund(fare != null ? fare.getPartialRefund() : null)
                .fullRefund(fare != null ? fare.getFullRefund() : null)
                .priorityBoarding(fare != null ? fare.getPriorityBoarding() : null)
                .loungeAccess(fare != null ? fare.getLoungeAccess() : null)
                .complimentaryMeals(fare != null ? fare.getComplimentaryMeals() : null)
                // Legacy — seat-service still reads this to mark seats BOOKED
                .seatInstanceIds(booking.getSeatInstanceIds())
                .build();

        kafkaTemplate.send("booking.confirmed", event);

    }


}
