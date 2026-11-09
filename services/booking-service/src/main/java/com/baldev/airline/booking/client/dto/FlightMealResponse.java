package com.baldev.airline.booking.client.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Client-side copy of ancillary-service's API model; only this service's view of the contract. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightMealResponse {
    private Long id;
    private Long flightId;
    private MealResponse meal;
    private Boolean available;
    private BigDecimal price;
    private Integer displayOrder;
    private String notes;
}
