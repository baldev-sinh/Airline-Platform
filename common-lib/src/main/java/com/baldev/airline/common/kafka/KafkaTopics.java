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
    public static final String BOOKING_REFUNDED = "booking.refunded";
    public static final String REFUND_REQUESTED = "refund.requested";
    public static final String PAYMENT_REFUNDED = "payment.refunded";
    public static final String PAYMENT_REFUND_FAILED = "payment.refund-failed";
    public static final String USER_REGISTERED = "user.registered";

    /** Every topic of the platform; each is declared together with its {@code -dlt} dead-letter topic. */
    public static final java.util.List<String> ALL = java.util.List.of(
            FLIGHT_INSTANCE_CREATED, PAYMENT_COMPLETED, PAYMENT_FAILED, BOOKING_CONFIRMED, BOOKING_CANCELLED,
            BOOKING_REFUNDED, REFUND_REQUESTED, PAYMENT_REFUNDED, PAYMENT_REFUND_FAILED, USER_REGISTERED);

    private KafkaTopics() {
    }
}
