package com.baldev.airline.flightops.service.support;

import com.baldev.airline.flightops.client.dto.AircraftResponse;
import com.baldev.airline.flightops.client.dto.AirlineResponse;
import com.baldev.airline.flightops.client.dto.AirportResponse;
import com.baldev.airline.flightops.payload.response.FlightInstanceResponse;
import com.baldev.airline.flightops.payload.response.FlightResponse;
import com.baldev.airline.flightops.payload.response.FlightScheduleResponse;
import com.baldev.airline.flightops.service.support.ReferenceData.Ref;
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
 * remote call per distinct airline, aircraft and airport instead of four calls per row. Lookups go through
 * {@link ReferenceData}, so an unavailable dependency yields a {@code partial} response instead of an error.
 */
@Component
@RequiredArgsConstructor
public class FlightResponseAssembler {

    private final ReferenceData referenceData;

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
        Ref<AircraftResponse> aircraft = lookups.aircraft(flight.getAircraftId());
        Ref<AirlineResponse> airline = lookups.airline(flight.getAirlineId());
        Ref<AirportResponse> from = lookups.airport(flight.getDepartureAirportId());
        Ref<AirportResponse> to = lookups.airport(flight.getArrivalAirportId());
        FlightResponse response = FlightMapper.toResponse(flight, aircraft.value(), airline.value(), from.value(), to.value());
        response.setPartial(!(aircraft.available() && airline.available() && from.available() && to.available()));
        return response;
    }

    private FlightInstanceResponse toInstanceResponse(FlightInstance instance, Lookups lookups) {
        Ref<AircraftResponse> aircraft = lookups.aircraft(instance.getFlight().getAircraftId());
        Ref<AirlineResponse> airline = lookups.airline(instance.getAirlineId());
        Ref<AirportResponse> from = lookups.airport(instance.getDepartureAirportId());
        Ref<AirportResponse> to = lookups.airport(instance.getArrivalAirportId());
        FlightInstanceResponse response = FlightInstanceMapper.toResponse(
                instance, aircraft.value(), airline.value(), from.value(), to.value());
        response.setPartial(!(aircraft.available() && airline.available() && from.available() && to.available()));
        return response;
    }

    private FlightScheduleResponse toScheduleResponse(FlightSchedule schedule, Lookups lookups) {
        return FlightScheduleMapper.toResponse(
                schedule,
                lookups.airport(schedule.getArrivalAirportId()).value(),
                lookups.airport(schedule.getDepartureAirportId()).value());
    }

    /** Per-conversion memoization of remote lookups. Not shared across requests, so it never serves stale data. */
    private final class Lookups {
        private final Map<Long, Ref<AirlineResponse>> airlines = new HashMap<>();
        private final Map<Long, Ref<AircraftResponse>> aircraft = new HashMap<>();
        private final Map<Long, Ref<AirportResponse>> airports = new HashMap<>();

        Ref<AirlineResponse> airline(Long id) {
            return airlines.computeIfAbsent(id, referenceData::airline);
        }

        Ref<AircraftResponse> aircraft(Long id) {
            return aircraft.computeIfAbsent(id, referenceData::aircraft);
        }

        Ref<AirportResponse> airport(Long id) {
            return airports.computeIfAbsent(id, referenceData::airport);
        }
    }
}
