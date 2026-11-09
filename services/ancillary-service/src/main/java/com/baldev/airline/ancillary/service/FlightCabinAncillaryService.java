package com.baldev.airline.ancillary.service;

import com.baldev.airline.ancillary.payload.request.PriceQuoteRequest;
import java.math.BigDecimal;
import com.baldev.airline.common.enums.AncillaryType;
import com.baldev.airline.ancillary.payload.request.FlightCabinAncillaryRequest;
import com.baldev.airline.ancillary.payload.response.FlightCabinAncillaryResponse;
import java.util.List;

public interface FlightCabinAncillaryService {

    FlightCabinAncillaryResponse create(FlightCabinAncillaryRequest request);

    FlightCabinAncillaryResponse getById(Long id);

    List<FlightCabinAncillaryResponse> getByFlightAndCabinClass(Long flightId, Long cabinClassId);

    List<FlightCabinAncillaryResponse> getAllByIds(List<Long> ids);

    FlightCabinAncillaryResponse getByFlightIdAndCabinClassIdAndType(Long flightId,
                                                                     Long cabinClassId,
                                                                     AncillaryType type
    );

    List<FlightCabinAncillaryResponse> getAllByFlightIdAndCabinClassIdAndType(
            Long flightId, Long cabinClassId, AncillaryType type
    );

    FlightCabinAncillaryResponse update(Long id, FlightCabinAncillaryRequest request);

    void delete(Long id);

    BigDecimal calculateAncillaryPrice(PriceQuoteRequest request);


}
