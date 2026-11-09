package com.baldev.airline.location.service;

import com.baldev.airline.location.payload.request.AirportRequest;
import com.baldev.airline.location.payload.response.AirportResponse;
import java.util.List;

public interface AirportService {

    AirportResponse createAirport(AirportRequest request);
    AirportResponse getAirportById(Long id);

    List<AirportResponse> getAllAirports();

    AirportResponse updateAirport(Long id,AirportRequest request);
    void deleteAirport(Long id);
    List<AirportResponse> getAirportByCityId(Long cityId);
}
