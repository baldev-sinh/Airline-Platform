package com.baldev.airline.booking.service.impl;

import com.baldev.airline.booking.client.*;
import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.common.enums.PaymentGateway;
import com.baldev.airline.booking.mapper.BookingMapper;
import com.baldev.airline.booking.model.Booking;
import com.baldev.airline.booking.model.Passenger;
import com.baldev.airline.common.payload.dto.PaymentDTO;
import com.baldev.airline.common.payload.request.BookingRequest;
import com.baldev.airline.common.payload.request.PassengerRequest;
import com.baldev.airline.common.payload.request.PaymentInitiateRequest;
import com.baldev.airline.common.payload.response.*;
import com.baldev.airline.booking.repository.BookingRepository;
import com.baldev.airline.booking.service.BookingService;
import com.baldev.airline.booking.service.PassengerService;
import com.baldev.airline.booking.service.TicketService;
import com.baldev.airline.booking.service.integration.FareIntegrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {


    private final BookingRepository bookingRepository;
    private final PassengerService passengerService;
    private final TicketService ticketService;
    private final FlightClient flightClient;
    private final FareIntegrationService fareIntegrationService;
    private final SeatClient seatClient;
    private final AncillaryClient ancillaryClient;
    private final PaymentClient paymentClient;
    private final AirlineClient airlineClient;

    @Override
    public PaymentInitiateResponse createBooking(BookingRequest request, Long userId) {

//      step 1: create uniq booking reference
        String bookingReference = generateBookingReference();

//      step 2: create passenger
        Set<Passenger> passengers = new HashSet<>();
        for (PassengerRequest passengerRequest : request.getPassengers()) {
            Passenger passenger = passengerService.createPassenger(passengerRequest, userId);
            passengers.add(passenger);
        }

//        step 3 flight exist
        FlightResponse flightResponse = flightClient.getFlightById(request.getFlightId());

//        step 4: create booking with pending status
        Booking booking = BookingMapper.toEntity(request,
                userId, passengers, bookingReference);

//       airline id from flightResponse
        booking.setAirlineId(flightResponse.getAirline().getId());

//        step 5: set seat instance ids
        List<Long> seatInstanceIds = request.getPassengers().stream()
                .map(PassengerRequest::getSeatInstanceId)
                .collect(Collectors.toList());

        booking.setSeatInstanceIds(seatInstanceIds);

        booking = bookingRepository.save(booking);

//        set booking reference on passengers
        for (Passenger passenger : passengers) {
            passenger.setBooking(booking);
        }

//        step 6: generate tickets for booking
        ticketService.generateTicketsForBooking(booking);

//      step 7 : calculate price
//        1. calculate fareTotal
        Double fareTotal = fareIntegrationService.calculateFareTotal(request.getFareId());
//        2. seat price
        Double seatPrice = seatClient.calculateSeatPrice(booking.getSeatInstanceIds());
//        3. ancillary price
        Double ancillaryPrice = ancillaryClient
                .calculateAncillariesPrice(booking.getAncillaryIds());

//        4. meal price
        Double mealPrice = ancillaryClient.calculateMealPrice(booking.getMealIds());

        Double totalPrice = mealPrice + seatPrice + ancillaryPrice;

//        step 8: initiate payment using payment service
        PaymentInitiateRequest paymentRequest = PaymentInitiateRequest.builder()
                .userId(userId)
                .bookingId(booking.getId())
                .amount(totalPrice)
                .gateway(PaymentGateway.RAZORPAY)
                .description("payment for booking : " + bookingReference)
                .build();


        return paymentClient.initiatePayment(paymentRequest);
    }

    @Override
    public BookingResponse updateBooking(Long id, BookingRequest request) {
        return null;
    }

    @Override
    public BookingResponse getBookingById(Long id) throws Exception {
        Booking booking = bookingRepository.findById(id).orElseThrow(
                () -> new Exception("booking not found with id")
        );
        return convertToBookingResponse(booking);
    }

    @Override
    public List<BookingResponse> getAllBookingsByAirline(Long userId,
                                                         String searchQuery,
                                                         BookingStatus status,
                                                         Long flightInstanceId,
                                                         String sortDirection) {
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Sort sort = Sort.by(direction, "bookingDate");
        List<Booking> bookings = bookingRepository.findByAirlineWithFilter(
                airlineResponse.getId(),
                searchQuery,
                status,
                flightInstanceId,
                sort
        );

        return bookings.stream().map(
                this::convertToBookingResponse
        ).toList();


    }

    @Override
    public List<BookingResponse> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserId(userId)
                .stream().map(this::convertToBookingResponse).toList();
    }

    @Override
    public BookingResponse cancelBooking(Long id) throws Exception {
        Booking booking = bookingRepository.findById(id).orElseThrow(
                () -> new Exception("booking not found with id")
        );
        booking.setStatus(BookingStatus.CANCELLED);
        Booking updated = bookingRepository.save(booking);
        return convertToBookingResponse(updated);
    }

    @Override
    public void deleteBooking(Long id) throws Exception {
        Booking booking = bookingRepository.findById(id).orElseThrow(
                () -> new Exception("booking not found with id")
        );
        bookingRepository.delete(booking);
    }

    private String generateBookingReference() {
        String reference;
        do {
            reference = "BK" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        } while (
                bookingRepository.existsByBookingReference(reference)
        );
        return reference;
    }

    private BookingResponse convertToBookingResponse(Booking booking) {
//        todo : when enable feign cilent use actual responses

        List<FlightCabinAncillaryResponse> ancillaryResponses = new ArrayList<>();
        List<FlightMealResponse> mealResponses = new ArrayList<>();
        PaymentDTO paymentDTO = new PaymentDTO();
        FareResponse fareResponse = new FareResponse();
        FlightResponse flightResponse = new FlightResponse();

        List<SeatInstanceResponse> seatInstanceResponses = new ArrayList<>();
        FlightInstanceResponse flightInstanceResponse = new FlightInstanceResponse();

        return BookingMapper.toResponse(
                booking,
                paymentDTO,
                fareResponse,
                flightResponse,
                flightInstanceResponse,
                ancillaryResponses,
                mealResponses,
                seatInstanceResponses
        );


    }
}
