package com.baldev.airline.seat.payload.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightInstanceCabinRequest {

    @NotNull
    private Long flightId;

    @NotNull(message = "Flight instance ID is required")
    private Long flightInstanceId;

    @NotNull
    private Long cabinClassId;

    @NotNull
    @Positive
    private BigDecimal baseFare;

    @NotNull
    @PositiveOrZero
    private BigDecimal taxesAndFees;

    @NotNull
    @PositiveOrZero
    private BigDecimal airlineFees;

    private BigDecimal currentPrice;
    private Boolean isActive;

}
