package com.baldev.airline.seat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Returns abandoned checkout holds to inventory. Safe to run on every replica concurrently: expired rows are
 * row-locked before release, and releasing an already-released seat is a no-op.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatHoldExpiryJob {

    private final SeatInventoryService seatInventoryService;

    @Scheduled(fixedDelayString = "${airline.seat.hold-expiry-check-interval:PT1M}")
    public void releaseExpiredHolds() {
        try {
            int released;
            do {
                released = seatInventoryService.releaseExpiredHolds();
            } while (released > 0);
        } catch (RuntimeException e) {
            log.error("Failed to release expired seat holds", e);
        }
    }
}
