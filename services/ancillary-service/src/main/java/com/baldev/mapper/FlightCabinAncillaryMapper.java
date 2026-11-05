package com.baldev.mapper;

import com.baldev.model.FlightCabinAncillary;
import com.baldev.payload.response.FlightCabinAncillaryResponse;
import com.baldev.payload.response.InsuranceCoverageResponse;

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
