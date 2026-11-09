package com.baldev.airline.flightops.payload.request;

import java.math.BigDecimal;

import com.baldev.airline.common.enums.CabinClassType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FlightSearchRequest {

    private Long departureAirportId;
    private Long arrivalAirportId;

    @NotNull(message = "Departure date is required")
    private LocalDate departureDate;

    @Min(value = 1, message = "at least 1 passenger is required")
    private Integer passengers;

    @NotNull(message = "cabin class is required")
    private CabinClassType cabinClass;

//    filter parameters
    private List<Long> airlines;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String departureTimeRange;
    private String arrivalTimeRange;
    private Integer maxDuration;
    private String sortBy;
    private String sortOrder;
}
