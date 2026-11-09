package com.baldev.airline.notification.service;

import com.baldev.airline.common.event.BookingCancelledEvent;
import com.baldev.airline.common.event.BookingRefundedEvent;
import com.baldev.airline.notification.config.NotificationProperties;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationComposerTest {

    private final NotificationComposer composer = new NotificationComposer(new NotificationProperties(
            "no-reply@test.local", "https://fly.example", true, 6, Duration.ofMinutes(1), Duration.ofDays(180)));

    @Test
    void abandonedCheckoutIsNotWorthAnEmail() {
        assertThat(composer.bookingCancelled(cancelled(false, "Checkout failed", null))).isEmpty();
        assertThat(composer.bookingCancelled(cancelled(false, "Seat hold expired", null))).isEmpty();
    }

    @Test
    void failedPaymentIsAnnounced() {
        NotificationMessage message = composer.bookingCancelled(cancelled(false, "Payment failed: Checkout expired", null))
                .orElseThrow();
        assertThat(message.subject()).startsWith("Payment not completed");
        assertThat(message.summary()).contains("No money was taken");
    }

    @Test
    void ticketedCancellationMentionsTheRefund() {
        NotificationMessage message = composer.bookingCancelled(cancelled(true, "Cancelled by customer", new BigDecimal("4200")))
                .orElseThrow();
        assertThat(message.eventKey()).isEqualTo("booking.cancelled:42");
        assertThat(message.summary()).contains("INR 4200.00");
        assertThat(message.variables()).containsEntry("refund", "INR 4200.00");
    }

    @Test
    void eachRefundIsItsOwnNotification() {
        BookingRefundedEvent event = BookingRefundedEvent.builder().bookingId(42L).bookingReference("ABC123")
                .amount(new BigDecimal("10")).currency("INR").providerRefundId("rfnd_1").build();
        assertThat(composer.bookingRefunded(event).eventKey()).isEqualTo("booking.refunded:42:rfnd_1");
    }

    private static BookingCancelledEvent cancelled(boolean wasConfirmed, String reason, BigDecimal refund) {
        return BookingCancelledEvent.builder().bookingId(42L).bookingReference("ABC123").userId(5L)
                .contactEmail("a@b.c").wasConfirmed(wasConfirmed).reason(reason).refundAmount(refund).currency("INR")
                .flightNumber("BA101").departureAirportCode("BOM").arrivalAirportCode("DEL").build();
    }
}
