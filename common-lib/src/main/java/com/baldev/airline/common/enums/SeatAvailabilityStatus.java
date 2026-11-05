package com.baldev.airline.common.enums;

/**
 * Lifecycle of a seat on one flight instance:
 * {@code AVAILABLE -> HELD (during checkout, with expiry) -> BOOKED (payment confirmed) -> OCCUPIED (boarded)}.
 * A hold that expires or whose booking is cancelled returns to {@code AVAILABLE}. {@code BLOCKED} is set by
 * operations (crew rest, broken seat) and is never sellable.
 */
public enum SeatAvailabilityStatus {
    AVAILABLE,
    HELD,
    BOOKED,
    OCCUPIED,
    BLOCKED
}
