package com.baldev.airline.flightops.service.impl;


import com.baldev.airline.flightops.client.AirlineClient;
import com.baldev.airline.flightops.client.LocationClient;
import com.baldev.airline.common.enums.FlightStatus;
import com.baldev.airline.flightops.mapper.FlightMapper;
import com.baldev.airline.flightops.model.Flight;
import com.baldev.airline.common.payload.request.FlightRequest;
import com.baldev.airline.common.payload.response.AircraftResponse;
import com.baldev.airline.common.payload.response.AirlineResponse;
import com.baldev.airline.common.payload.response.AirportResponse;
import com.baldev.airline.common.payload.response.FlightResponse;
import com.baldev.airline.flightops.repository.FlightRepository;
import com.baldev.airline.flightops.service.FlightService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FlightServiceImpl implements FlightService {


    private final FlightRepository flightRepository;
    private final AirlineClient airlineClient;
    private final LocationClient locationClient;


    @Override
    public FlightResponse createFlight(Long userId, FlightRequest flightRequest) throws Exception {

//        fetch airline by owner id
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);

        if (flightRepository.existsByFlightNumber(flightRequest.getFlightNumber())) {
            throw new Exception("flight with already exist");
        }
        Flight flight = FlightMapper.toEntity(flightRequest);
        flight.setAirlineId(airlineResponse.getId());
        Flight saved = flightRepository.save(flight);

        return convertToFlightResponse(saved);
    }

    @Override
    public Page<FlightResponse> getFlightsByAirline(Long userId,
                                                    Long departureAirportId,
                                                    Long arrivalAirportId,
                                                    Pageable pageable
    ) {
//        fetch airline by owner id
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);

        return flightRepository.findByAirlineId(airlineResponse.getId(),
                departureAirportId,
                arrivalAirportId,
                pageable).map(this::convertToFlightResponse);
    }

    @Override
    public FlightResponse getFlightById(Long id) throws Exception {
        Flight flight = flightRepository.findById(id).orElseThrow(
                () -> new Exception("flight not found with id")
        );
        return convertToFlightResponse(flight);
    }

//    F-451 updated
//    F-451

//    F-450
//    F-450 depId


    @Override
    public FlightResponse updateFlight(Long id, FlightRequest flightRequest) throws Exception {
        Flight existing = flightRepository.findById(id).orElseThrow(
                () -> new Exception("flight not found with id")
        );
        if (flightRequest.getFlightNumber() != null &&
                flightRepository.existsByFlightNumberAndIdNot(flightRequest.getFlightNumber(), id)) {
            throw new Exception("flight with already exist");
        }
        FlightMapper.updateEntity(flightRequest, existing);
        Flight updated = FlightMapper.toEntity(flightRequest);
        return convertToFlightResponse(updated);
    }

    @Override
    public FlightResponse changeStatus(Long id, FlightStatus status) throws Exception {
        Flight existing = flightRepository.findById(id).orElseThrow(
                () -> new Exception("flight not found with id")
        );
        existing.setStatus(status);
        Flight updated = flightRepository.save(existing);
        return convertToFlightResponse(updated);
    }

    @Override
    public void deleteFlight(Long userId, Long id) throws Exception {
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);
        Flight existing = flightRepository
                .findByAirlineIdAndId(airlineResponse.getId(), id)
                .orElseThrow(() -> new Exception("flight not found with id"));
        flightRepository.delete(existing);
    }

    public FlightResponse convertToFlightResponse(Flight flight) {
//        service to service communication

        AircraftResponse aircraft = airlineClient.getAircraftById(flight.getAircraftId());

        AirlineResponse airline = airlineClient.getAirlineById(flight.getAirlineId());

        AirportResponse departureAirport = locationClient.getAirportById(flight.getDepartureAirportId());
        AirportResponse arrivalAirport = locationClient.getAirportById(flight.getArrivalAirportId());

        return FlightMapper.toResponse(
                flight,
                aircraft,
                airline,
                departureAirport,
                arrivalAirport
        );
    }
}
