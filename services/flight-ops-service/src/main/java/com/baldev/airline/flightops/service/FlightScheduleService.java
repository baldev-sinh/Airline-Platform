package com.baldev.airline.flightops.service;

import com.baldev.airline.flightops.payload.request.FlightScheduleRequest;
import com.baldev.airline.flightops.payload.response.FlightScheduleResponse;
import java.util.List;

public interface FlightScheduleService {

    FlightScheduleResponse createFlightSchedule(Long userId, FlightScheduleRequest request);

    FlightScheduleResponse getFlightScheduleById(Long id);

    List<FlightScheduleResponse> getFlightScheduleByAirline(Long userId);

    FlightScheduleResponse updateFlightSchedule(Long userId, Long id, FlightScheduleRequest request);

    void deleteFlightSchedule(Long userId, Long id);
}
