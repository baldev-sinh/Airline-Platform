package com.baldev.airline.booking.service.pricing;

import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.booking.client.dto.FareRulesResponse;
import com.baldev.airline.common.util.Money;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * How much of a ticketed booking goes back to the customer when they cancel it. Pure function, unit-tested.
 *
 * <ol>
 *   <li>A flight that has departed cannot be cancelled (it is a no-show, handled by the airline).</li>
 *   <li>No fare rules: the airline set no restrictions, so everything paid is refunded.</li>
 *   <li>Non-refundable fare, or cancelled inside the fare's refund deadline: nothing is refunded. The booking is
 *       still cancelled, so the seats go back on sale.</li>
 *   <li>Otherwise: everything paid minus the fare's cancellation fee per passenger, never below zero.</li>
 * </ol>
 */
public final class RefundPolicy {

    private RefundPolicy() {
    }

    public record RefundQuote(BigDecimal amount, String reason) {
        public boolean isRefundable() {
            return amount.signum() > 0;
        }
    }

    /**
     * @param paid      what the customer paid for the whole booking
     * @param rules     the fare's rules, or {@code null} if the fare has none
     * @param departure scheduled departure (UTC)
     * @param now       current time (UTC)
     */
    public static RefundQuote quote(BigDecimal paid, int passengers, FareRulesResponse rules,
                                    LocalDateTime departure, LocalDateTime now) {
        if (departure == null || !departure.isAfter(now)) {
            throw new BusinessRuleException("FLIGHT_DEPARTED", "A flight that has departed cannot be cancelled");
        }
        BigDecimal total = Money.normalize(paid);
        if (rules == null) {
            return new RefundQuote(total, "Full refund");
        }
        if (!Boolean.TRUE.equals(rules.getIsRefundable())) {
            return new RefundQuote(Money.normalize(BigDecimal.ZERO), "Non-refundable fare");
        }
        Integer deadlineDays = rules.getRefundDeadlineDays();
        if (deadlineDays != null && deadlineDays > 0 && now.isAfter(departure.minusDays(deadlineDays))) {
            return new RefundQuote(Money.normalize(BigDecimal.ZERO),
                    "Refunds close " + deadlineDays + " day(s) before departure");
        }
        BigDecimal fee = Money.normalize(rules.getCancellationFee()).multiply(BigDecimal.valueOf(Math.max(passengers, 1)));
        BigDecimal refund = total.subtract(fee).max(BigDecimal.ZERO);
        return new RefundQuote(Money.normalize(refund),
                fee.signum() > 0 ? "Refund minus cancellation fee " + Money.normalize(fee) : "Full refund");
    }
}
