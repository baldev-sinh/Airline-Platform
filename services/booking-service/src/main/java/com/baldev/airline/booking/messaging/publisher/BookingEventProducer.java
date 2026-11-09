package com.baldev.airline.booking.messaging.publisher;

import com.baldev.airline.common.event.BookingCancelledEvent;
import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.event.BookingRefundedEvent;
import com.baldev.airline.common.event.RefundRequestedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.common.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records booking lifecycle events in the transactional outbox, keyed by booking id. Runs synchronously inside
 * the transaction that changed the booking, so the event commits (or rolls back) with it; the outbox relay
 * publishes it to Kafka after commit. Publishing outside a transaction fails loudly (OutboxPublisher).
 */
@Component
@RequiredArgsConstructor
public class BookingEventProducer {

    private final OutboxPublisher outbox;

    @EventListener
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        outbox.publish(KafkaTopics.BOOKING_CONFIRMED, String.valueOf(event.getBookingId()), event);
    }

    @EventListener
    public void onBookingCancelled(BookingCancelledEvent event) {
        outbox.publish(KafkaTopics.BOOKING_CANCELLED, String.valueOf(event.getBookingId()), event);
    }

    @EventListener
    public void onRefundRequested(RefundRequestedEvent event) {
        outbox.publish(KafkaTopics.REFUND_REQUESTED, String.valueOf(event.getBookingId()), event);
    }

    @EventListener
    public void onBookingRefunded(BookingRefundedEvent event) {
        outbox.publish(KafkaTopics.BOOKING_REFUNDED, String.valueOf(event.getBookingId()), event);
    }
}
