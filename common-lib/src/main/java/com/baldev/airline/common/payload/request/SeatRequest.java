package com.baldev.airline.common.payload.request;

import java.math.BigDecimal;

import com.baldev.airline.common.enums.SeatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatRequest {

    @NotBlank(message="seat number is required")
    private String seatNumber;

    @NotNull(message = "Seat row is required")
    private Integer seatRow;

    private Character columnLetter;

    @NotNull(message = "seat type is required")
    private SeatType seatType;

    @NotNull(message = "Seat map ID is required")
    private Long seatMapId;

    private Long cabinClassId;

    private Boolean isAvailable;
    private Boolean isBlocked;
    private Boolean isEmergencyExit;
    private Boolean isActive;

    private BigDecimal basePrice;
    private BigDecimal premiumSurcharge;

    private Boolean hasExtraLegroom;

    private Boolean hasPowerOutlet;
    private Boolean hasTvScreen;

    private Boolean hasExtraWidth;

    private Integer seatPitch;
    private Integer seatWidth;

}
