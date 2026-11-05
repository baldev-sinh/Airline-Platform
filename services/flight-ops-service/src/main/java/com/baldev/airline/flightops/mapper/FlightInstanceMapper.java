package com.baldev.airline.flightops.mapper;


import com.baldev.airline.common.enums.FlightStatus;
import com.baldev.airline.flightops.model.Flight;
import com.baldev.airline.flightops.model.FlightInstance;
import com.baldev.airline.common.payload.request.FlightInstanceRequest;
import com.baldev.airline.common.payload.response.AircraftResponse;
import com.baldev.airline.common.payload.response.AirlineResponse;
import com.baldev.airline.common.payload.response.AirportResponse;
import com.baldev.airline.common.payload.response.FlightInstanceResponse;

public class FlightInstanceMapper {

    public static FlightInstance toEntity(FlightInstanceRequest request, Flight flight) {
        if (flight == null) return null;

        return FlightInstance.builder()
                .flight(flight)
                .airlineId(flight.getAirlineId())
                .scheduleId(request.getScheduleId())
                .departureAirportId(
                        request.getDepartureAirportId()!=null?request.getDepartureAirportId():null
                ).arrivalAirportId(
                        request.getArrivalAirportId()!=null?request.getArrivalAirportId():null
                ).departureDateTime(request.getDepartureDateTime())
                .arrivalDateTime(request.getArrivalDateTime())
                .status(FlightStatus.SCHEDULED)
                .minAdvanceBookingDays(request.getMinAdvanceBookingDays())
                .maxAdvanceBookingDays(request.getMaxAdvanceBookingDays())
                .isActive(request.getIsActive()!=null?request.getIsActive():true)
                .build();
    }

    public static FlightInstanceResponse toResponse(FlightInstance fi,
                                                    AircraftResponse aircraftResponse,
                                                    AirlineResponse airline,
                                                    AirportResponse departureAirport,
                                                    AirportResponse arrivalAirport) {
        if (fi == null) return null;
        return FlightInstanceResponse.builder()
                .id(fi.getId())
                .flightId(fi.getFlight() != null ? fi.getFlight().getId() : null)
                .flightNumber(fi.getFlight() != null ? fi.getFlight().getFlightNumber() : null)
                .aircraftId(fi.getFlight().getAircraftId())
                .aircraftModal(aircraftResponse.getModel())
                .aircraftCode(aircraftResponse.getCode())
                .airlineId(fi.getAirlineId())
                .airlineName(airline.getName())
                .airlineLogo(airline.getLogoUrl())
                .departureAirport(departureAirport)
                .arrivalAirport(arrivalAirport)
                .departureDateTime(fi.getDepartureDateTime())
                .arrivalDateTime(fi.getArrivalDateTime())
                .formattedDuration(fi.getFormatedDuration())
                .totalSeats(fi.getTotalSeats())
                .availableSeats(fi.getAvailableSeats())
                .status(fi.getStatus())
                .minAdvanceBookingDays(fi.getMinAdvanceBookingDays())
                .maxAdvanceBookingDays(fi.getMaxAdvanceBookingDays())
                .isActive(fi.getIsActive())
                .build();
    }

    public static void updateEntity(FlightInstanceRequest request, FlightInstance existing) {
        if (request == null || existing == null) return;
        if (request.getDepartureAirportId() != null) existing.setDepartureAirportId(request.getDepartureAirportId());
        if (request.getArrivalAirportId() != null) existing.setArrivalAirportId(request.getArrivalAirportId());
        if (request.getDepartureDateTime() != null) existing.setDepartureDateTime(request.getDepartureDateTime());
        if (request.getArrivalDateTime() != null) existing.setArrivalDateTime(request.getArrivalDateTime());
        if (request.getAvailableSeats() != null) existing.setAvailableSeats(request.getAvailableSeats());
        if (request.getStatus() != null) existing.setStatus(request.getStatus());
        if (request.getMinAdvanceBookingDays() != null) existing.setMinAdvanceBookingDays(request.getMinAdvanceBookingDays());
        if (request.getMaxAdvanceBookingDays() != null) existing.setMaxAdvanceBookingDays(request.getMaxAdvanceBookingDays());
        if (request.getIsActive() != null) existing.setIsActive(request.getIsActive());

    }
}
