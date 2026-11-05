package com.baldev.airline.booking.messaging.publisher;

import com.baldev.airline.common.event.BookingCancelledEvent;
import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes booking lifecycle events after the state change commits, keyed by booking id.
 * (Known gap shared with payment-service: a crash between commit and send loses the event; a transactional
 * outbox closes it. Consumers are idempotent, so adding one later is safe.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        send(KafkaTopics.BOOKING_CONFIRMED, event.getBookingId(), event);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingCancelled(BookingCancelledEvent event) {
        send(KafkaTopics.BOOKING_CANCELLED, event.getBookingId(), event);
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
