package com.baldev.airline.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/** Published by payment-service when the provider has processed a refund. Keyed by bookingId. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRefundedEvent {
    private String refundKey;
    private Long paymentId;
    private Long bookingId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    /** Provider refund id (Razorpay rfnd_...). */
    private String providerRefundId;
    private Instant refundedAt;
}
