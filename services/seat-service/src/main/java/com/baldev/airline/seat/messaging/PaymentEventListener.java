package com.baldev.airline.seat.messaging;

import com.baldev.airline.common.event.PaymentCompletedEvent;


import com.baldev.airline.seat.client.BookingClient;
import com.baldev.airline.common.enums.SeatAvailabilityStatus;
import com.baldev.airline.common.payload.response.BookingResponse;
import com.baldev.airline.common.payload.response.SeatInstanceResponse;
import com.baldev.airline.seat.service.SeatInstanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PaymentEventListener {


    private final BookingClient bookingClient;
    private final SeatInstanceService seatInstanceService;

    @KafkaListener(topics = "payment.completed", groupId = "seat-service-group")
    public void handleBookingConfirmed(PaymentCompletedEvent event) {

        BookingResponse bookingResponse = bookingClient.getBookingById(event.getBookingId());

        List<SeatInstanceResponse> seatInstances = bookingResponse.getSeatInstances();

        for (SeatInstanceResponse seatInstanceResponse : seatInstances) {
            seatInstanceService.updateSeatInstanceStatus(
                    seatInstanceResponse.getId(),
                    SeatAvailabilityStatus.BOOKED
            );
        }

    }


}
