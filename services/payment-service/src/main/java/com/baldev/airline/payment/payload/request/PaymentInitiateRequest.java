package com.baldev.airline.payment.payload.request;

import com.baldev.airline.common.enums.PaymentGateway;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Internal only (booking-service -> payment-service). The amount is computed server-side by booking-service;
 * clients can never supply it, which is why this endpoint is not exposed through the gateway.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInitiateRequest {

    @NotNull(message = "user id is mandatory")
    private Long userId;

    @NotNull(message = "booking id is required")
    private Long bookingId;

    @NotNull(message = "Payment gateway is mandatory")
    private PaymentGateway gateway;

    @NotNull(message = "Amount is mandatory")
    @DecimalMin(value = "1.00", message = "Amount must be at least 1.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal amount;

    @Size(min = 3, max = 3)
    private String currency;

    @Size(max = 255)
    private String description;
}
