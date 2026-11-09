package com.baldev.airline.booking.service.pricing;

import com.baldev.airline.booking.service.pricing.RefundPolicy.RefundQuote;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.booking.client.dto.FareRulesResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundPolicyTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 8, 10, 0);
    private static final LocalDateTime IN_TEN_DAYS = NOW.plusDays(10);
    private static final BigDecimal PAID = new BigDecimal("10000.00");

    @Test
    void noRulesRefundsEverything() {
        RefundQuote quote = RefundPolicy.quote(PAID, 2, null, IN_TEN_DAYS, NOW);
        assertThat(quote.amount()).isEqualByComparingTo("10000.00");
        assertThat(quote.isRefundable()).isTrue();
    }

    @Test
    void cancellationFeeIsChargedPerPassenger() {
        RefundQuote quote = RefundPolicy.quote(PAID, 2, rules(true, "1500", null), IN_TEN_DAYS, NOW);
        assertThat(quote.amount()).isEqualByComparingTo("7000.00");
    }

    @Test
    void refundNeverGoesNegative() {
        RefundQuote quote = RefundPolicy.quote(new BigDecimal("1000"), 1, rules(true, "2500", null), IN_TEN_DAYS, NOW);
        assertThat(quote.amount()).isEqualByComparingTo("0.00");
        assertThat(quote.isRefundable()).isFalse();
    }

    @Test
    void nonRefundableFareRefundsNothing() {
        assertThat(RefundPolicy.quote(PAID, 1, rules(false, "0", null), IN_TEN_DAYS, NOW).amount()).isZero();
    }

    @Test
    void insideTheRefundDeadlineRefundsNothing() {
        assertThat(RefundPolicy.quote(PAID, 1, rules(true, "0", 14), IN_TEN_DAYS, NOW).amount()).isZero();
        assertThat(RefundPolicy.quote(PAID, 1, rules(true, "0", 7), IN_TEN_DAYS, NOW).amount())
                .isEqualByComparingTo("10000.00");
    }

    @Test
    void departedFlightsCannotBeCancelled() {
        assertThatThrownBy(() -> RefundPolicy.quote(PAID, 1, null, NOW.minusMinutes(1), NOW))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("departed");
    }

    private static FareRulesResponse rules(boolean refundable, String fee, Integer deadlineDays) {
        return FareRulesResponse.builder()
                .isRefundable(refundable)
                .cancellationFee(new BigDecimal(fee))
                .refundDeadlineDays(deadlineDays)
                .build();
    }
}
