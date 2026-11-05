package com.baldev.airline.common.util;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void sumIsExactWhereDoubleIsNot() {
        // 0.1 + 0.2 == 0.30000000000000004 in double arithmetic.
        assertThat(Money.sum(new BigDecimal("0.1"), new BigDecimal("0.2"))).isEqualByComparingTo("0.30");
    }

    @Test
    void sumTreatsNullAsZero() {
        assertThat(Money.sum(new BigDecimal("10.00"), null, new BigDecimal("2.50"))).isEqualByComparingTo("12.50");
    }

    @Test
    void normalizesToTwoDecimalsWithBankersRounding() {
        assertThat(Money.normalize(new BigDecimal("2.345"))).isEqualByComparingTo("2.34");
        assertThat(Money.normalize(new BigDecimal("2.355"))).isEqualByComparingTo("2.36");
        assertThat(Money.normalize(null)).isEqualByComparingTo("0.00");
    }

    @Test
    void convertsToMinorUnitsForPaymentGateways() {
        assertThat(Money.toMinorUnits(new BigDecimal("1234.50"))).isEqualTo(123450L);
        assertThat(Money.toMinorUnits(new BigDecimal("0.01"))).isEqualTo(1L);
    }

    @Test
    void minorUnitConversionNeverSilentlyOverflows() {
        assertThatThrownBy(() -> Money.toMinorUnits(new BigDecimal("1e30"))).isInstanceOf(ArithmeticException.class);
    }
}
