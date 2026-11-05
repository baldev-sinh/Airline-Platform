package com.baldev.airline.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.Objects;

/**
 * Monetary arithmetic helpers. All amounts in the platform are {@link BigDecimal} in the major currency unit
 * (e.g. rupees), with 2 decimal places and banker's rounding. Never use {@code double} for money.
 */
public final class Money {

    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    public static final String DEFAULT_CURRENCY = "INR";

    private Money() {
    }

    /** {@code null}-safe: treats a missing amount as zero. */
    public static BigDecimal orZero(BigDecimal amount) {
        return amount != null ? amount : BigDecimal.ZERO;
    }

    public static BigDecimal normalize(BigDecimal amount) {
        return orZero(amount).setScale(SCALE, ROUNDING);
    }

    public static BigDecimal sum(BigDecimal... amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal amount : amounts) {
            total = total.add(orZero(amount));
        }
        return normalize(total);
    }

    public static BigDecimal sum(Collection<BigDecimal> amounts) {
        return normalize(amounts.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** Converts to the smallest currency unit (paise/cents) as payment gateways require. */
    public static long toMinorUnits(BigDecimal amount) {
        return normalize(amount).movePointRight(SCALE).longValueExact();
    }
}
