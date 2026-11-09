package com.baldev.airline.flightops.service;


import com.baldev.airline.flightops.payload.request.FlightSearchRequest;
import com.baldev.airline.flightops.payload.response.FlightInstanceResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightSearchService {

    Page<FlightInstanceResponse> searchFlights(FlightSearchRequest request, Pageable pageable);
}
