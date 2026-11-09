package com.baldev.airline.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Published by booking-service when money must go back to the customer: a confirmed booking was cancelled, or a
 * payment arrived for a booking that was already cancelled (hold expired). payment-service refunds it.
 * Keyed by bookingId. {@code refundKey} is unique per refund and makes the request idempotent end to end.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestedEvent {
    private String refundKey;
    private Long bookingId;
    private String bookingReference;
    private Long paymentId;
    private Long userId;
    /** Amount to refund; never more than what was captured. */
    private BigDecimal amount;
    private String currency;
    private String reason;
    private Instant requestedAt;
}
