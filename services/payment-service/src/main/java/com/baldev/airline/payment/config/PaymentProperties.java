package com.baldev.airline.payment.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param currency    ISO 4217 settlement currency
 * @param linkExpiry  how long a checkout link stays payable. Keep it shorter than the seat hold duration
 *                    (seat-service {@code airline.seat.hold-duration}) so a paid booking never loses its seats.
 */
@Validated
@ConfigurationProperties(prefix = "airline.payment")
public record PaymentProperties(
        @DefaultValue("INR") String currency,
        @DefaultValue("PT16M") Duration linkExpiry,
        Razorpay razorpay) {

    public record Razorpay(
            @NotBlank String keyId,
            @NotBlank String keySecret,
            @NotBlank String webhookSecret,
            @NotBlank String callbackBaseUrl) {
    }
}
