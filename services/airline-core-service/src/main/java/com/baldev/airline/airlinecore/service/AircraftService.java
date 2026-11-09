package com.baldev.airline.airlinecore.service;



import com.baldev.airline.airlinecore.payload.request.AircraftRequest;
import com.baldev.airline.airlinecore.payload.response.AircraftResponse;
import java.util.List;

public interface AircraftService {

    AircraftResponse createAircraft(AircraftRequest request, Long ownerId);
    AircraftResponse getAircraftById(Long id);
    List<AircraftResponse> listAllAircraftByOwner(Long ownerId);
    AircraftResponse updateAircraft(Long id, AircraftRequest request, Long ownerId);
    void deleteAircraft(Long id, Long ownerId);

}
