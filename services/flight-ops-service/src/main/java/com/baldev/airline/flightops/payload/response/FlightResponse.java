package com.baldev.airline.flightops.payload.response;

import com.baldev.airline.flightops.client.dto.AircraftResponse;
import com.baldev.airline.flightops.client.dto.AirlineResponse;
import com.baldev.airline.flightops.client.dto.AirportResponse;

import java.math.BigDecimal;

import com.baldev.airline.common.enums.FlightStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FlightResponse {

    private Long id;
    private String flightNumber;
    private AirlineResponse airline;
    private AircraftResponse aircraft;
    private AirportResponse departureAirport;
    private AirportResponse arrivalAirport;
    private LocalDateTime departureTime;
    private LocalDateTime arrivalTime;
    private FlightStatus status;
    private BigDecimal lowestPrice;
    private Integer totalAvailableSeats;
    private Instant createdAt;
    private Instant updatedAt;

    /**
     * True when airline, aircraft or airport details could not be loaded (that service was unavailable) and only
     * their ids are filled in. Clients can show the flight and refresh the details later.
     */
    private Boolean partial;
}
