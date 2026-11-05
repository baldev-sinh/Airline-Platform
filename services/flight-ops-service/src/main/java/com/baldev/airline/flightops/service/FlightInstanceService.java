package com.baldev.airline.flightops.service;

import com.baldev.airline.common.payload.request.FlightInstanceRequest;
import com.baldev.airline.common.payload.response.FlightInstanceResponse;
import com.baldev.airline.flightops.model.Flight;
import com.baldev.airline.flightops.model.FlightInstance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface FlightInstanceService {

    FlightInstanceResponse createFlightInstance(Long userId, FlightInstanceRequest request);

    /** Creates one dated instance of an airline's flight (used by schedule expansion); publishes the created event. */
    FlightInstance createInstance(Flight flight, Long scheduleId, LocalDateTime departure, LocalDateTime arrival);

    FlightInstanceResponse getFlightInstanceById(Long id);

    Page<FlightInstanceResponse> getByAirlineId(Long userId,
                                                Long departureAirportId,
                                                Long arrivalAirportId,
                                                Long flightId,
                                                LocalDate onDate,
                                                Pageable pageable);

    FlightInstanceResponse updateFlightInstance(Long userId, Long id, FlightInstanceRequest request);

    void deleteFlightInstance(Long userId, Long id);
}
