package com.baldev.event.listener;


import com.baldev.client.FlightClient;
import com.baldev.client.PricingClient;
import com.baldev.client.UserClient;
import com.baldev.enums.BookingStatus;
import com.baldev.event.PaymentCompletedEvent;
import com.baldev.event.PaymentFailedEvent;
import com.baldev.event.publisher.BookingEventProducer;
import com.baldev.model.Booking;
import com.baldev.payload.dto.UserDTO;
import com.baldev.payload.response.FareResponse;
import com.baldev.payload.response.FlightInstanceResponse;
import com.baldev.repository.BookingRepository;
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
