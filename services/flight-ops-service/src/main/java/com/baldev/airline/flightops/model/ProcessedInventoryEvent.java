package com.baldev.airline.flightops.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Idempotency record: one row per inventory-affecting booking event already applied. Kafka delivers at least
 * once, so without this a redelivered event would adjust seat counts twice.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
public class ProcessedInventoryEvent {

    /** {@code <EVENT_TYPE>:<bookingId>}, e.g. {@code CONFIRMED:42}. */
    @Id
    @Column(length = 64)
    private String eventKey;

    @Column(nullable = false)
    private Instant processedAt;
}
