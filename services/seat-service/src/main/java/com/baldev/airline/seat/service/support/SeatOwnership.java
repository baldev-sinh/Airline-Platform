package com.baldev.airline.seat.service.support;

import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.seat.client.AirlineClient;
import com.baldev.airline.seat.client.FlightOpsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Cross-tenant write protection for seat configuration: an airline owner may only change cabins and seat maps of
 * their own aircraft, and cabin inventory of their own flight instances. The gateway only checks the role; this
 * checks the tenant. Foreign resources answer 404 so their existence is not revealed. System admins pass.
 */
@Component
@RequiredArgsConstructor
public class SeatOwnership {

    private final AirlineClient airlineClient;
    private final FlightOpsClient flightOpsClient;

    public void requireOwnedAircraft(CurrentUser user, Long aircraftId) {
        if (user.isSystemAdmin()) {
            return;
        }
        Long owner = airlineClient.getAircraftById(aircraftId).getAirlineId();
        if (!Objects.equals(owner, callerAirlineId(user))) {
            throw ResourceNotFoundException.of("Aircraft", aircraftId);
        }
    }

    public void requireOwnedFlightInstance(CurrentUser user, Long flightInstanceId) {
        if (user.isSystemAdmin()) {
            return;
        }
        Long owner = flightOpsClient.getFlightInstanceById(flightInstanceId).getAirlineId();
        if (!Objects.equals(owner, callerAirlineId(user))) {
            throw ResourceNotFoundException.of("Flight instance", flightInstanceId);
        }
    }

    public void requireAirline(CurrentUser user, Long ownerAirlineId, String resource, Long id) {
        if (!user.isSystemAdmin() && !Objects.equals(ownerAirlineId, callerAirlineId(user))) {
            throw ResourceNotFoundException.of(resource, id);
        }
    }

    private Long callerAirlineId(CurrentUser user) {
        return airlineClient.getAirlineByOwner(user.id()).getId();
    }
}
