package com.baldev.airline.flightops.service;


import com.baldev.airline.common.payload.request.FlightSearchRequest;
import com.baldev.airline.common.payload.response.FlightInstanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightSearchService {

    Page<FlightInstanceResponse> searchFlights(FlightSearchRequest request, Pageable pageable);
}
