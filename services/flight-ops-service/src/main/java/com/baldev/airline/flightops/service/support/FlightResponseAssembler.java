package com.baldev.airline.flightops.service.support;

import com.baldev.airline.common.payload.response.AircraftResponse;
import com.baldev.airline.common.payload.response.AirlineResponse;
import com.baldev.airline.common.payload.response.AirportResponse;
import com.baldev.airline.common.payload.response.FlightInstanceResponse;
import com.baldev.airline.common.payload.response.FlightResponse;
import com.baldev.airline.common.payload.response.FlightScheduleResponse;
import com.baldev.airline.flightops.client.AirlineClient;
import com.baldev.airline.flightops.client.LocationClient;
import com.baldev.airline.flightops.mapper.FlightInstanceMapper;
import com.baldev.airline.flightops.mapper.FlightMapper;
import com.baldev.airline.flightops.mapper.FlightScheduleMapper;
import com.baldev.airline.flightops.model.Flight;
import com.baldev.airline.flightops.model.FlightInstance;
import com.baldev.airline.flightops.model.FlightSchedule;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds API responses enriched with airline/aircraft/airport data from other services.
 *
 * <p>Each list conversion uses a request-scoped {@link Lookups} cache, so a page of N instances costs one
 * remote call per distinct airline, aircraft and airport instead of four calls per row.
 */
@Component
@RequiredArgsConstructor
public class FlightResponseAssembler {

    private final AirlineClient airlineClient;
    private final LocationClient locationClient;

    public FlightResponse toFlightResponse(Flight flight) {
        return toFlightResponse(flight, new Lookups());
    }

    public List<FlightResponse> toFlightResponses(List<Flight> flights) {
        Lookups lookups = new Lookups();
        return flights.stream().map(f -> toFlightResponse(f, lookups)).toList();
    }

    public FlightInstanceResponse toInstanceResponse(FlightInstance instance) {
        return toInstanceResponse(instance, new Lookups());
    }

    public List<FlightInstanceResponse> toInstanceResponses(List<FlightInstance> instances) {
        Lookups lookups = new Lookups();
        return instances.stream().map(i -> toInstanceResponse(i, lookups)).toList();
    }

    public FlightScheduleResponse toScheduleResponse(FlightSchedule schedule) {
        return toScheduleResponse(schedule, new Lookups());
    }

    public List<FlightScheduleResponse> toScheduleResponses(List<FlightSchedule> schedules) {
        Lookups lookups = new Lookups();
        return schedules.stream().map(s -> toScheduleResponse(s, lookups)).toList();
    }

    private FlightResponse toFlightResponse(Flight flight, Lookups lookups) {
        return FlightMapper.toResponse(
                flight,
                lookups.aircraft(flight.getAircraftId()),
                lookups.airline(flight.getAirlineId()),
                lookups.airport(flight.getDepartureAirportId()),
                lookups.airport(flight.getArrivalAirportId()));
    }

    private FlightInstanceResponse toInstanceResponse(FlightInstance instance, Lookups lookups) {
        return FlightInstanceMapper.toResponse(
                instance,
                lookups.aircraft(instance.getFlight().getAircraftId()),
                lookups.airline(instance.getAirlineId()),
                lookups.airport(instance.getDepartureAirportId()),
                lookups.airport(instance.getArrivalAirportId()));
    }

    private FlightScheduleResponse toScheduleResponse(FlightSchedule schedule, Lookups lookups) {
        return FlightScheduleMapper.toResponse(
                schedule,
                lookups.airport(schedule.getArrivalAirportId()),
                lookups.airport(schedule.getDepartureAirportId()));
    }

    /** Per-conversion memoization of remote lookups. Not shared across requests, so it never serves stale data. */
    private final class Lookups {
        private final Map<Long, AirlineResponse> airlines = new HashMap<>();
        private final Map<Long, AircraftResponse> aircraft = new HashMap<>();
        private final Map<Long, AirportResponse> airports = new HashMap<>();

        AirlineResponse airline(Long id) {
            return airlines.computeIfAbsent(id, airlineClient::getAirlineById);
        }

        AircraftResponse aircraft(Long id) {
            return aircraft.computeIfAbsent(id, airlineClient::getAircraftById);
        }

        AirportResponse airport(Long id) {
            return airports.computeIfAbsent(id, locationClient::getAirportById);
        }
    }
}
