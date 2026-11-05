package com.baldev.airline.common.kafka;

/**
 * Kafka topic names: the single source of truth shared by producers and consumers.
 * Naming convention: {@code <aggregate>.<past-tense-event>}. Dead-letter topics append {@code -dlt}.
 */
public final class KafkaTopics {

    public static final String FLIGHT_INSTANCE_CREATED = "flight-instance.created";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";
    public static final String BOOKING_CONFIRMED = "booking.confirmed";
    public static final String BOOKING_CANCELLED = "booking.cancelled";

    private KafkaTopics() {
    }
}
