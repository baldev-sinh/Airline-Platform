package com.baldev.airline.booking.messaging.listener;


import com.baldev.airline.booking.client.FlightClient;
import com.baldev.airline.booking.client.PricingClient;
import com.baldev.airline.booking.client.UserClient;
import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.booking.messaging.publisher.BookingEventProducer;
import com.baldev.airline.booking.model.Booking;
import com.baldev.airline.common.payload.dto.UserDTO;
import com.baldev.airline.common.payload.response.FareResponse;
import com.baldev.airline.common.payload.response.FlightInstanceResponse;
import com.baldev.airline.booking.repository.BookingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentEventListener {

    private final BookingRepository bookingRepository;
    private final FlightClient flightClient;
    private final PricingClient pricingClient;
    private final UserClient userClient;
    private final BookingEventProducer bookingEventProducer;

    @KafkaListener(topics = "payment.completed", groupId = "booking-service-group")
    @Transactional
    public void handlePaymentCompleted(PaymentCompletedEvent event) throws Exception {

        System.out.println("Received PaymentCompletedEvent "
                + event.getBookingId() + "-"
                + event.getPaymentId());

        Booking booking = bookingRepository.findById(event.getBookingId())
                .orElse(null);

        if (booking == null) {
            return;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        FlightInstanceResponse flightInstanceResponse = flightClient
                .getFlightInstanceById(booking.getFlightInstanceId());
        FareResponse fareResponse = pricingClient.getFareById(booking.getFareId());
        UserDTO userDTO = userClient.getUserById(booking.getUserId());


        bookingEventProducer.sendBookingConfirmed(booking, event, flightInstanceResponse,
                fareResponse, userDTO);

    }

    @KafkaListener(topics = "payment.failed", groupId = "booking-service-group")
    public void handlePaymentFailed(PaymentFailedEvent event) throws Exception {
        Booking booking = bookingRepository.findById(event.getBookingId())
                .orElse(null);

        if (booking == null) {
            return;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        bookingRepository.save(booking);
    }
}
