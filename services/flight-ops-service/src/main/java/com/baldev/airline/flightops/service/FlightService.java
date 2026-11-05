package com.baldev.airline.flightops.service;


import com.baldev.airline.common.enums.FlightStatus;
import com.baldev.airline.common.payload.request.FlightRequest;
import com.baldev.airline.common.payload.response.FlightResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface FlightService {

    FlightResponse createFlight(Long userId, FlightRequest flightRequest) throws Exception;
    Page<FlightResponse> getFlightsByAirline(Long userId,
                                             Long departureAirportId,
                                             Long arrivalAirportId,
                                             Pageable pageable
    );
    FlightResponse getFlightById(Long id) throws Exception;
    FlightResponse updateFlight(Long id, FlightRequest flightRequest) throws Exception;
    FlightResponse changeStatus(Long id, FlightStatus status) throws Exception;
    void deleteFlight(Long userId, Long id) throws Exception;
}
