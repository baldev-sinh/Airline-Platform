package com.baldev.airline.airlinecore.service;



import com.baldev.airline.common.payload.request.AircraftRequest;
import com.baldev.airline.common.payload.response.AircraftResponse;

import java.util.List;

public interface AircraftService {

    AircraftResponse createAircraft(AircraftRequest request, Long ownerId) throws Exception;
    AircraftResponse getAircraftById(Long id) throws Exception;
    List<AircraftResponse> listAllAircraftByOwner(Long ownerId) throws Exception;
    AircraftResponse updateAircraft(Long id, AircraftRequest request, Long ownerId) throws Exception;
    void deleteAircraft(Long id, Long ownerId) throws Exception;

}
