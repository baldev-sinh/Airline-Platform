package com.baldev.airline.ancillary.service.support;

import com.baldev.airline.ancillary.client.AirlineClient;
import com.baldev.airline.ancillary.client.FlightClient;
import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.ancillary.client.dto.AirlineResponse;
import com.baldev.airline.ancillary.client.dto.FlightResponse;
import com.baldev.airline.common.web.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AncillaryOwnershipTest {

    private static final CurrentUser OWNER = new CurrentUser(5L, "o@x", Set.of(UserRole.ROLE_AIRLINE_OWNER));

    private final AirlineClient airlines = mock(AirlineClient.class);
    private final FlightClient flights = mock(FlightClient.class);
    private final AncillaryOwnership ownership = new AncillaryOwnership(airlines, flights);

    @BeforeEach
    void callerOwnsAirline10() {
        when(airlines.getAirlineByOwner(5L)).thenReturn(airline(10L));
    }

    @Test
    void anotherAirlinesMealIsNotFound() {
        assertThatCode(() -> ownership.requireAirline(OWNER, 10L, "Meal", 1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ownership.requireAirline(OWNER, 99L, "Meal", 2L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void extrasCanOnlyBeAttachedToOwnFlights() {
        FlightResponse own = new FlightResponse();
        own.setAirline(airline(10L));
        FlightResponse foreign = new FlightResponse();
        foreign.setAirline(airline(99L));
        when(flights.getFlightById(1L)).thenReturn(own);
        when(flights.getFlightById(2L)).thenReturn(foreign);

        assertThatCode(() -> ownership.requireOwnedFlight(OWNER, 1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ownership.requireOwnedFlight(OWNER, 2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    private static AirlineResponse airline(Long id) {
        AirlineResponse airline = new AirlineResponse();
        airline.setId(id);
        return airline;
    }
}
