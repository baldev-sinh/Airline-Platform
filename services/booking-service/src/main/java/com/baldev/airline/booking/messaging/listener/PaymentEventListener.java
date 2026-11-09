package com.baldev.airline.booking.messaging.listener;

import com.baldev.airline.booking.service.BookingService;
import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.common.event.PaymentRefundedEvent;
import com.baldev.airline.common.event.RefundFailedEvent;
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

    @KafkaListener(topics = KafkaTopics.PAYMENT_REFUNDED)
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        log.info("Received PaymentRefunded bookingId={} refund={}", event.getBookingId(), event.getProviderRefundId());
        bookingService.onPaymentRefunded(event);
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_REFUND_FAILED)
    public void onRefundFailed(RefundFailedEvent event) {
        log.info("Received RefundFailed bookingId={} refundKey={}", event.getBookingId(), event.getRefundKey());
        bookingService.onRefundFailed(event);
    }
}
