package com.baldev.airline.pricing.service.support;

import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.payload.response.FlightResponse;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.pricing.client.AirlineClient;
import com.baldev.airline.pricing.client.FlightOwnershipClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Ensures an airline owner can only price their own airline's flights. */
@Component
@RequiredArgsConstructor
public class FlightOwnershipVerifier {

    private final FlightOwnershipClient flightClient;
    private final AirlineClient airlineClient;

    public void requireOwnedFlight(CurrentUser user, Long flightId) {
        if (user.isSystemAdmin()) {
            return;
        }
        FlightResponse flight = flightClient.getFlightById(flightId);
        Long callerAirlineId = airlineClient.getAirlineByOwner(user.id()).getId();
        Long flightAirlineId = flight.getAirline() != null ? flight.getAirline().getId() : null;
        if (!Objects.equals(callerAirlineId, flightAirlineId)) {
            // 404, not 403: do not confirm that another airline's flight exists.
            throw ResourceNotFoundException.of("Flight", flightId);
        }
    }
}
