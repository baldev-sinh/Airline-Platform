package com.baldev.airline.flightops.service;

import com.baldev.airline.common.payload.request.FlightScheduleRequest;
import com.baldev.airline.common.payload.response.FlightScheduleResponse;


import java.util.List;

public interface FlightScheduleService {

    FlightScheduleResponse createFlightSchedule(Long userId,
                                                FlightScheduleRequest request
    ) throws Exception;
    FlightScheduleResponse getFlightScheduleById(Long id) throws Exception;
    List<FlightScheduleResponse> getFlightScheduleByAirline(Long userId);
    FlightScheduleResponse updateFlightSchedule(Long id,
                                                FlightScheduleRequest flightScheduleRequest) throws Exception;
    void deleteFlightSchedule(Long id) throws Exception;

}
