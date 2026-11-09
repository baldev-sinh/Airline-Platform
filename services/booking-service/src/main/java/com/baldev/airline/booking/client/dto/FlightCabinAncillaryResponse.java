package com.baldev.airline.booking.client.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Client-side copy of ancillary-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightCabinAncillaryResponse {

    private Long id;
    private Long flightId;
    private Long cabinClassId;
    private AncillaryResponse ancillary;
    private Boolean available;
    private Integer maxQuantity;
    private BigDecimal price;
    private Boolean includedInFare;

}
