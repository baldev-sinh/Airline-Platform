package com.baldev.airline.seat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Seat inventory settings.
 *
 * @param holdDuration    how long seats stay held during checkout. Must exceed the payment link expiry so a
 *                        customer who pays at the last moment still gets their seats.
 * @param windowSurcharge default surcharge for window seats when the seat has none configured
 * @param aisleSurcharge  default surcharge for aisle seats when the seat has none configured
 */
@ConfigurationProperties(prefix = "airline.seat")
public record SeatProperties(
        @DefaultValue("PT20M") Duration holdDuration,
        @DefaultValue("1000.00") BigDecimal windowSurcharge,
        @DefaultValue("500.00") BigDecimal aisleSurcharge) {
}
