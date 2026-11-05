package com.baldev.airline.booking.messaging.listener;

import com.baldev.airline.booking.service.BookingService;
import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final BookingService bookingService;

    @KafkaListener(topics = KafkaTopics.PAYMENT_COMPLETED)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        log.info("Received PaymentCompleted bookingId={} paymentId={}", event.getBookingId(), event.getPaymentId());
        bookingService.onPaymentCompleted(event);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_FAILED)
    public void onPaymentFailed(PaymentFailedEvent event) {
        log.info("Received PaymentFailed bookingId={} paymentId={}", event.getBookingId(), event.getPaymentId());
        bookingService.onPaymentFailed(event);
    }
}
