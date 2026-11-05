package com.baldev.airline.payment.messaging;

import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes payment outcomes only after the settling transaction commits, keyed by booking id so every event
 * for one booking lands on the same partition in order.
 *
 * <p>Known gap: a crash between commit and send loses the event. The production-grade fix is a transactional
 * outbox (event row written in the same transaction, relayed to Kafka); the gateway webhook plus the
 * idempotent consumers make a replay safe in the meantime.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        send(KafkaTopics.PAYMENT_COMPLETED, event.getBookingId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPaymentFailed(PaymentFailedEvent event) {
        send(KafkaTopics.PAYMENT_FAILED, event.getBookingId(), event);
    }

    private void send(String topic, Long bookingId, Object event) {
        kafkaTemplate.send(topic, String.valueOf(bookingId), event).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish {} for bookingId={}", topic, bookingId, ex);
            } else {
                log.info("Published {} for bookingId={}", topic, bookingId);
            }
        });
    }
}
