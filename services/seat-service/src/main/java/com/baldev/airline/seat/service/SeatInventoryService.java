package com.baldev.airline.seat.service;

import com.baldev.airline.seat.payload.request.SeatHoldRequest;
import com.baldev.airline.seat.payload.response.SeatHoldResponse;
/**
 * Seat inventory per flight instance and its lifecycle: create, hold during checkout, book on confirmation,
 * release on cancellation or hold expiry. All transitions are idempotent so Kafka redelivery is safe.
 */
public interface SeatInventoryService {

    /** Creates seat instances for every cabin of the aircraft. No-op if inventory already exists. */
    void createInventory(Long flightId, Long flightInstanceId, Long aircraftId);

    /** Atomically holds all requested seats for the booking, or none (409 if any is unavailable). */
    SeatHoldResponse holdSeats(SeatHoldRequest request);

    /** Converts the booking's held seats into booked seats. */
    void confirmSeats(Long bookingId);

    /** Returns the booking's held or booked seats to inventory. */
    void releaseSeats(Long bookingId, boolean wasConfirmed);

    /** Releases holds whose expiry has passed. @return number of seats released. */
    int releaseExpiredHolds();
}
