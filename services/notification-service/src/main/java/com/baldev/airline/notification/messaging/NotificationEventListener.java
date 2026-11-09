package com.baldev.airline.notification.messaging;

import com.baldev.airline.common.event.BookingCancelledEvent;
import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.event.BookingRefundedEvent;
import com.baldev.airline.common.event.UserRegisteredEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.notification.service.NotificationComposer;
import com.baldev.airline.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the customer-facing domain events. Every handler is idempotent (unique event key per channel),
 * because delivery is at least once; a record that keeps failing ends up on {@code <topic>-dlt}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationComposer composer;
    private final NotificationService notificationService;

    @KafkaListener(topics = KafkaTopics.USER_REGISTERED)
    public void onUserRegistered(UserRegisteredEvent event) {
        log.info("Received UserRegistered userId={}", event.getUserId());
        notificationService.notify(composer.welcome(event));
    }

    @KafkaListener(topics = KafkaTopics.BOOKING_CONFIRMED)
    public void onBookingConfirmed(BookingConfirmedEvent event) {
        log.info("Received BookingConfirmed bookingId={}", event.getBookingId());
        notificationService.notify(composer.bookingConfirmed(event));
    }

    @KafkaListener(topics = KafkaTopics.BOOKING_CANCELLED)
    public void onBookingCancelled(BookingCancelledEvent event) {
        composer.bookingCancelled(event).ifPresentOrElse(notificationService::notify,
                () -> log.debug("BookingCancelled bookingId={} needs no notification", event.getBookingId()));
    }

    @KafkaListener(topics = KafkaTopics.BOOKING_REFUNDED)
    public void onBookingRefunded(BookingRefundedEvent event) {
        log.info("Received BookingRefunded bookingId={}", event.getBookingId());
        notificationService.notify(composer.bookingRefunded(event));
    }
}
