package com.baldev.airline.flightops.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Client-side copy of seat-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CabinClassResponse {
    private Long id;
    private String name;
    private String code;
    private String description;
    private Long aircraftId;
    private Integer displayOrder;
    private Boolean isActive;
    private Boolean isBookable;
    private Integer typicalSeatPitch;
    private Integer typicalSeatWidth;
    private String seatType;
    private Instant createdAt;
    private Instant updatedAt;
    private SeatMapResponse seatMap;
}
