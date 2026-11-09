package com.baldev.airline.payment.messaging;

import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.common.event.PaymentRefundedEvent;
import com.baldev.airline.common.event.RefundFailedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.common.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records payment outcomes in the transactional outbox inside the settling transaction, keyed by booking id so
 * every event for one booking lands on the same partition in order. The outbox relay publishes after commit.
 */
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final OutboxPublisher outbox;

    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        outbox.publish(KafkaTopics.PAYMENT_COMPLETED, String.valueOf(event.getBookingId()), event);
    }

    @EventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        outbox.publish(KafkaTopics.PAYMENT_FAILED, String.valueOf(event.getBookingId()), event);
    }

    @EventListener
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        outbox.publish(KafkaTopics.PAYMENT_REFUNDED, String.valueOf(event.getBookingId()), event);
    }

    @EventListener
    public void onRefundFailed(RefundFailedEvent event) {
        outbox.publish(KafkaTopics.PAYMENT_REFUND_FAILED, String.valueOf(event.getBookingId()), event);
    }
}
