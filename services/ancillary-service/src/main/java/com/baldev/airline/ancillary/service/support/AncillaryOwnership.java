package com.baldev.airline.ancillary.service.support;

import com.baldev.airline.ancillary.client.AirlineClient;
import com.baldev.airline.ancillary.client.FlightClient;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.ancillary.client.dto.FlightResponse;
import com.baldev.airline.common.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Cross-tenant write protection for extras: an airline owner may only change their own airline's meals,
 * ancillaries and insurance, and attach them only to their own flights. The gateway only checks the role; this
 * checks the tenant. Foreign resources answer 404 so their existence is not revealed. System admins pass.
 */
@Component
@RequiredArgsConstructor
public class AncillaryOwnership {

    private final AirlineClient airlineClient;
    private final FlightClient flightClient;

    public void requireAirline(CurrentUser user, Long ownerAirlineId, String resource, Long id) {
        if (!user.isSystemAdmin() && !Objects.equals(ownerAirlineId, callerAirlineId(user))) {
            throw ResourceNotFoundException.of(resource, id);
        }
    }

    public void requireOwnedFlight(CurrentUser user, Long flightId) {
        if (user.isSystemAdmin()) {
            return;
        }
        FlightResponse flight = flightClient.getFlightById(flightId);
        Long owner = flight.getAirline() != null ? flight.getAirline().getId() : null;
        if (!Objects.equals(owner, callerAirlineId(user))) {
            throw ResourceNotFoundException.of("Flight", flightId);
        }
    }

    private Long callerAirlineId(CurrentUser user) {
        return airlineClient.getAirlineByOwner(user.id()).getId();
    }
}
