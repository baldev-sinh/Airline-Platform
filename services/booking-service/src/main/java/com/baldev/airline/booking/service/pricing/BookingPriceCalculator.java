package com.baldev.airline.booking.service.pricing;

import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.util.Money;

import java.math.BigDecimal;

/**
 * Computes the amount charged for a booking. Pure function: no I/O, fully unit-tested.
 *
 * <p>total = farePerPassenger x passengers + seat surcharges + ancillaries + meals
 */
public final class BookingPriceCalculator {

    private BookingPriceCalculator() {
    }

    public record PriceBreakdown(BigDecimal fareAmount, BigDecimal seatAmount, BigDecimal ancillaryAmount,
                                 BigDecimal mealAmount, BigDecimal totalAmount) {
    }

    public static PriceBreakdown calculate(BigDecimal farePerPassenger, int passengerCount, BigDecimal seatAmount,
                                           BigDecimal ancillaryAmount, BigDecimal mealAmount) {
        if (passengerCount <= 0) {
            throw new BusinessRuleException("NO_PASSENGERS", "A booking needs at least one passenger");
        }
        if (farePerPassenger == null || farePerPassenger.signum() <= 0) {
            throw new BusinessRuleException("FARE_NOT_PRICED", "The selected fare has no valid price");
        }
        BigDecimal fareAmount = Money.normalize(farePerPassenger.multiply(BigDecimal.valueOf(passengerCount)));
        BigDecimal seats = Money.normalize(seatAmount);
        BigDecimal ancillaries = Money.normalize(ancillaryAmount);
        BigDecimal meals = Money.normalize(mealAmount);
        if (seats.signum() < 0 || ancillaries.signum() < 0 || meals.signum() < 0) {
            throw new BusinessRuleException("INVALID_PRICE", "Price components cannot be negative");
        }
        return new PriceBreakdown(fareAmount, seats, ancillaries, meals, Money.sum(fareAmount, seats, ancillaries, meals));
    }
}
