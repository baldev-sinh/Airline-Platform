package com.baldev.airline.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Published by payment-service when a refund cannot be made (provider rejected it). Keyed by bookingId. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundFailedEvent {
    private String refundKey;
    private Long paymentId;
    private Long bookingId;
    private String reason;
    private Instant failedAt;
}
