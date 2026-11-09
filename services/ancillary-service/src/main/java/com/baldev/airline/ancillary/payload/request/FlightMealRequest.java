package com.baldev.airline.ancillary.payload.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightMealRequest {

    @NotNull(message = "Flight ID is required")
    private Long flightId;

    @NotNull(message = "Meal ID is required")
    private Long mealId;

    private Boolean available;

    @DecimalMin(value = "0.0", message = "Price cannot be negative")
    private BigDecimal price;

    @Min(value = 0, message = "Display order cannot be negative")
    private Integer displayOrder;
}
