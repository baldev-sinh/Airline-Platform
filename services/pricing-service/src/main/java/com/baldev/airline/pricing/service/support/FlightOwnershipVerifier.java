package com.baldev.airline.pricing.service.support;

import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.pricing.client.dto.FlightResponse;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.pricing.client.AirlineClient;
import com.baldev.airline.pricing.client.FlightOwnershipClient;
import com.baldev.airline.pricing.repository.FareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Ensures an airline owner can only price their own airline's flights, and tells callers which airline owns a
 * flight or fare so they can stamp it server-side instead of trusting the client. Foreign resources are 404.
 */
@Component
@RequiredArgsConstructor
public class FlightOwnershipVerifier {

    private final FlightOwnershipClient flightClient;
    private final AirlineClient airlineClient;
    private final FareRepository fareRepository;

    /** @return the id of the airline that owns {@code flightId}. */
    public Long requireOwnedFlight(CurrentUser user, Long flightId) {
        FlightResponse flight = flightClient.getFlightById(flightId);
        Long flightAirlineId = flight.getAirline() != null ? flight.getAirline().getId() : null;
        if (user.isSystemAdmin()) {
            return flightAirlineId;
        }
        Long callerAirlineId = airlineClient.getAirlineByOwner(user.id()).getId();
        if (!Objects.equals(callerAirlineId, flightAirlineId)) {
            // 404, not 403: do not confirm that another airline's flight exists.
            throw ResourceNotFoundException.of("Flight", flightId);
        }
        return flightAirlineId;
    }

    /** @return the id of the airline that owns the fare's flight. */
    public Long requireOwnedFare(CurrentUser user, Long fareId) {
        Long flightId = fareRepository.findById(fareId)
                .orElseThrow(() -> ResourceNotFoundException.of("Fare", fareId))
                .getFlightId();
        try {
            return requireOwnedFlight(user, flightId);
        } catch (ResourceNotFoundException e) {
            throw ResourceNotFoundException.of("Fare", fareId);
        }
    }
}
