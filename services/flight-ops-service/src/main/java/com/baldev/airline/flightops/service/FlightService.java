package com.baldev.airline.flightops.service;

import com.baldev.airline.common.enums.FlightStatus;
import com.baldev.airline.flightops.payload.request.FlightRequest;
import com.baldev.airline.flightops.payload.response.FlightResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FlightService {

    FlightResponse createFlight(Long userId, FlightRequest flightRequest);

    Page<FlightResponse> getFlightsByAirline(Long userId, Long departureAirportId, Long arrivalAirportId,
                                             Pageable pageable);

    FlightResponse getFlightById(Long id);

    FlightResponse updateFlight(Long userId, Long id, FlightRequest flightRequest);

    FlightResponse changeStatus(Long userId, Long id, FlightStatus status);

    void deleteFlight(Long userId, Long id);
}
