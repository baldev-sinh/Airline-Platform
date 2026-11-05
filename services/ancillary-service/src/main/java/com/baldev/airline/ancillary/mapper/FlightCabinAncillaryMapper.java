package com.baldev.airline.ancillary.mapper;

import com.baldev.airline.ancillary.model.FlightCabinAncillary;
import com.baldev.airline.common.payload.response.FlightCabinAncillaryResponse;
import com.baldev.airline.common.payload.response.InsuranceCoverageResponse;

import java.util.List;

public class FlightCabinAncillaryMapper {

    public static FlightCabinAncillaryResponse toResponse(
            FlightCabinAncillary flightCabinAncillary,
            List<InsuranceCoverageResponse> coverages
    ) {
        if (flightCabinAncillary == null) return null;

        return FlightCabinAncillaryResponse.builder()
                .id(flightCabinAncillary.getId())
                .flightId(flightCabinAncillary.getFlightId())
                .cabinClassId(flightCabinAncillary.getCabinClassId())
                .ancillary(AncillaryMapper.toResponse(
                        flightCabinAncillary.getAncillary(), coverages
                ))
                .available(flightCabinAncillary.getAvailable())
                .maxQuantity(flightCabinAncillary.getMaxQuantity())
                .price(flightCabinAncillary.getPrice())
                .includedInFare(flightCabinAncillary.getIncludedInFare())
                .build();
    }
}
