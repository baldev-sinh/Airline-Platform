package com.baldev.airline.booking.service.pricing;

import com.baldev.airline.booking.service.pricing.BookingPriceCalculator.PriceBreakdown;
import com.baldev.airline.common.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingPriceCalculatorTest {

    @Test
    void chargesFarePerPassengerPlusExtras() {
        PriceBreakdown price = BookingPriceCalculator.calculate(
                new BigDecimal("4999.50"), 3, new BigDecimal("1500.00"), new BigDecimal("750.25"), new BigDecimal("400.00"));

        assertThat(price.fareAmount()).isEqualByComparingTo("14998.50");
        assertThat(price.totalAmount()).isEqualByComparingTo("17648.75");
    }

    @Test
    void fareIsAlwaysIncluded() {
        // Regression: the original implementation summed only seats, ancillaries and meals.
        PriceBreakdown price = BookingPriceCalculator.calculate(new BigDecimal("5000"), 1, null, null, null);
        assertThat(price.totalAmount()).isEqualByComparingTo("5000.00");
    }

    @Test
    void rejectsUnpricedFare() {
        assertThatThrownBy(() -> BookingPriceCalculator.calculate(BigDecimal.ZERO, 1, null, null, null))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> BookingPriceCalculator.calculate(null, 1, null, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsNegativeComponents() {
        assertThatThrownBy(() -> BookingPriceCalculator.calculate(
                new BigDecimal("100"), 1, new BigDecimal("-50"), null, null))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void rejectsZeroPassengers() {
        assertThatThrownBy(() -> BookingPriceCalculator.calculate(new BigDecimal("100"), 0, null, null, null))
                .isInstanceOf(BusinessRuleException.class);
    }
}
