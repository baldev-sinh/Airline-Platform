package com.baldev.airline.ancillary.service;

import com.baldev.airline.common.enums.AncillaryType;
import com.baldev.airline.common.payload.request.FlightCabinAncillaryRequest;
import com.baldev.airline.common.payload.response.FlightCabinAncillaryResponse;

import java.util.List;

public interface FlightCabinAncillaryService {

    FlightCabinAncillaryResponse create(FlightCabinAncillaryRequest request) throws Exception;

    FlightCabinAncillaryResponse getById(Long id) throws Exception;

    List<FlightCabinAncillaryResponse> getByFlightAndCabinClass(Long flightId, Long cabinClassId);

    List<FlightCabinAncillaryResponse> getAllByIds(List<Long> ids);

    FlightCabinAncillaryResponse getByFlightIdAndCabinClassIdAndType(Long flightId,
                                                                     Long cabinClassId,
                                                                     AncillaryType type
    );

    List<FlightCabinAncillaryResponse> getAllByFlightIdAndCabinClassIdAndType(
            Long flightId, Long cabinClassId, AncillaryType type
    );

    FlightCabinAncillaryResponse update(Long id, FlightCabinAncillaryRequest request) throws Exception;

    void delete(Long id) throws Exception;

    Double calculateAncillaryPrice(List<Long> ancillaryIds);


}
