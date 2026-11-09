package com.baldev.airline.common.enums;

/** Where a booking's refund stands. */
public enum RefundStatus {
    /** Nothing to refund (never paid, or the fare refunds nothing). */
    NONE,
    /** Requested from payment-service, not yet confirmed by the provider. */
    PENDING,
    REFUNDED,
    /** The provider rejected the refund: support has to resolve it. */
    FAILED
}
