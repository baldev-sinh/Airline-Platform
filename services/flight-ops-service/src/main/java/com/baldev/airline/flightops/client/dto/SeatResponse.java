package com.baldev.airline.flightops.client.dto;

import java.math.BigDecimal;
import com.baldev.airline.common.enums.SeatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Client-side copy of seat-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {

    private Long id;
    private String seatNumber;
    private Integer seatRow;
    private Character columnLetter;
    private SeatType seatType;

    private Boolean isAvailable;
    private Boolean isBlocked;
    private Boolean isEmergencyExit;
    private Boolean isActive;

    private BigDecimal basePrice;
    private BigDecimal premiumSurcharge;
    private BigDecimal totalPrice;

    private Boolean hasExtraLegroom;

    private Boolean hasPowerOutlet;
    private Boolean hasTvScreen;

    private Boolean hasExtraWidth;

    private Integer seatPitch;
    private Integer seatWidth;

    private Long seatMapId;
    private String seatMapName;
    private Long cabinClassId;
    private String cabinClassName;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    private Boolean isPremiumSeat;
    private Boolean isBookable;
    private String fullPosition;
    private String seatCharacteristics;
}
