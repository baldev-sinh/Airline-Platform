package com.baldev.airline.notification.service;

import com.baldev.airline.common.event.BookingCancelledEvent;
import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.event.BookingRefundedEvent;
import com.baldev.airline.common.event.PassengerNotificationData;
import com.baldev.airline.common.event.UserRegisteredEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.notification.config.NotificationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Decides what each event tells the customer. Pure mapping (no I/O), unit-tested.
 *
 * <p>Cancellations are only announced when the customer would care: a ticketed booking was cancelled, or a
 * payment failed. A checkout abandoned before paying is not worth an e-mail.
 */
@Component
public class NotificationComposer {

    static final String BRAND = "Baldev Airways";
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final NotificationProperties properties;

    public NotificationComposer(NotificationProperties properties) {
        this.properties = properties;
    }

    public NotificationMessage welcome(UserRegisteredEvent event) {
        Map<String, Object> vars = base();
        vars.put("name", firstName(event.getFullName()));
        return new NotificationMessage(KafkaTopics.USER_REGISTERED + ":" + event.getUserId(), "welcome",
                event.getUserId(), event.getEmail(), "Welcome to " + BRAND,
                "Your account is ready. Search for a flight and book in a few minutes.", vars);
    }

    public NotificationMessage bookingConfirmed(BookingConfirmedEvent event) {
        Map<String, Object> vars = base();
        vars.put("reference", event.getBookingReference());
        vars.put("airline", Objects.requireNonNullElse(event.getAirlineName(), BRAND));
        vars.put("flightNumber", event.getFlightNumber());
        vars.put("from", place(event.getDepartureCity(), event.getDepartureAirportCode()));
        vars.put("to", place(event.getArrivalCity(), event.getArrivalAirportCode()));
        vars.put("departure", format(event.getDepartureDateTime()));
        vars.put("arrival", format(event.getArrivalDateTime()));
        vars.put("cabin", event.getCabinClass() != null ? capitalize(event.getCabinClass()) : null);
        vars.put("passengers", Optional.ofNullable(event.getPassengers()).orElse(List.of()).stream()
                .map(p -> Map.of("name", fullName(p), "ticket", Objects.requireNonNullElse(p.getTicketNumber(), "")))
                .toList());
        vars.put("total", amount(event.getTotalAmount(), event.getCurrency()));
        vars.put("tripUrl", properties.siteUrl() + "/trips/" + event.getBookingId());
        String route = event.getDepartureAirportCode() + " to " + event.getArrivalAirportCode();
        return new NotificationMessage(KafkaTopics.BOOKING_CONFIRMED + ":" + event.getBookingId(), "booking-confirmed",
                event.getUserId(), event.getContactEmail(),
                "Booking confirmed: " + route + " (" + event.getBookingReference() + ")",
                "Your booking " + event.getBookingReference() + " for " + event.getFlightNumber() + ", " + route
                        + " on " + format(event.getDepartureDateTime()) + " is confirmed. Your e-tickets are attached to the trip.",
                vars);
    }

    /** @return empty when the cancellation is not worth telling the customer about. */
    public Optional<NotificationMessage> bookingCancelled(BookingCancelledEvent event) {
        boolean paymentFailed = event.getReason() != null && event.getReason().startsWith("Payment failed");
        if (!event.isWasConfirmed() && !paymentFailed) {
            return Optional.empty();
        }
        Map<String, Object> vars = base();
        vars.put("reference", event.getBookingReference());
        vars.put("flightNumber", event.getFlightNumber());
        vars.put("route", event.getDepartureAirportCode() + " to " + event.getArrivalAirportCode());
        vars.put("departure", format(event.getDepartureDateTime()));
        vars.put("paymentFailed", paymentFailed);
        vars.put("refund", positive(event.getRefundAmount()) ? amount(event.getRefundAmount(), event.getCurrency()) : null);
        vars.put("searchUrl", properties.siteUrl() + "/flights");
        String summary = paymentFailed
                ? "The payment for booking " + event.getBookingReference() + " did not go through, so the booking was released. No money was taken."
                : "Booking " + event.getBookingReference() + " is cancelled."
                + (positive(event.getRefundAmount())
                        ? " A refund of " + amount(event.getRefundAmount(), event.getCurrency()) + " is on its way."
                        : " This fare has no refund.");
        return Optional.of(new NotificationMessage(KafkaTopics.BOOKING_CANCELLED + ":" + event.getBookingId(),
                "booking-cancelled", event.getUserId(), event.getContactEmail(),
                (paymentFailed ? "Payment not completed: " : "Booking cancelled: ") + event.getBookingReference(),
                summary, vars));
    }

    public NotificationMessage bookingRefunded(BookingRefundedEvent event) {
        Map<String, Object> vars = base();
        vars.put("reference", event.getBookingReference());
        vars.put("amount", amount(event.getAmount(), event.getCurrency()));
        vars.put("refundId", event.getProviderRefundId());
        vars.put("flightNumber", event.getFlightNumber());
        return new NotificationMessage(
                KafkaTopics.BOOKING_REFUNDED + ":" + event.getBookingId() + ":" + event.getProviderRefundId(),
                "booking-refunded", event.getUserId(), event.getContactEmail(),
                "Refund processed: " + event.getBookingReference(),
                "We refunded " + amount(event.getAmount(), event.getCurrency()) + " for booking "
                        + event.getBookingReference() + ". Banks usually show it within 5 to 7 working days.",
                vars);
    }

    private Map<String, Object> base() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("brand", BRAND);
        vars.put("siteUrl", properties.siteUrl());
        return vars;
    }

    static String amount(BigDecimal value, String currency) {
        return (currency != null ? currency + " " : "") + Money.normalize(value).toPlainString();
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String format(LocalDateTime time) {
        return time != null ? WHEN.format(time) + " UTC" : "";
    }

    private static String place(String city, String code) {
        return city != null ? city + " (" + code + ")" : code;
    }

    private static String fullName(PassengerNotificationData p) {
        return (Objects.requireNonNullElse(p.getFirstName(), "") + " " + Objects.requireNonNullElse(p.getLastName(), "")).trim();
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "there";
        }
        return fullName.trim().split("\\s+")[0];
    }

    private static String capitalize(String value) {
        String lower = value.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
