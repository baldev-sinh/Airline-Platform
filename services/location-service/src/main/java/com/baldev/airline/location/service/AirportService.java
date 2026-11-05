package com.baldev.airline.location.service;

import com.baldev.airline.common.payload.request.AirportRequest;
import com.baldev.airline.common.payload.response.AirportResponse;

import java.util.List;

public interface AirportService {

    AirportResponse createAirport(AirportRequest request);
    AirportResponse getAirportById(Long id);

    List<AirportResponse> getAllAirports();

    AirportResponse updateAirport(Long id,AirportRequest request);
    void deleteAirport(Long id);
    List<AirportResponse> getAirportByCityId(Long cityId);
}
