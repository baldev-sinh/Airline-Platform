package com.baldev.airline.seat.service.support;

import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.seat.client.dto.AircraftResponse;
import com.baldev.airline.seat.client.dto.AirlineResponse;
import com.baldev.airline.seat.client.dto.FlightInstanceResponse;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.seat.client.AirlineClient;
import com.baldev.airline.seat.client.FlightOpsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeatOwnershipTest {

    private static final CurrentUser OWNER = new CurrentUser(5L, "o@x", Set.of(UserRole.ROLE_AIRLINE_OWNER));
    private static final CurrentUser ADMIN = new CurrentUser(1L, "a@x", Set.of(UserRole.ROLE_SYSTEM_ADMIN));

    private final AirlineClient airlines = mock(AirlineClient.class);
    private final FlightOpsClient flights = mock(FlightOpsClient.class);
    private final SeatOwnership ownership = new SeatOwnership(airlines, flights);

    @BeforeEach
    void callerOwnsAirline10() {
        AirlineResponse airline = new AirlineResponse();
        airline.setId(10L);
        when(airlines.getAirlineByOwner(5L)).thenReturn(airline);
    }

    @Test
    void ownAircraftPassesForeignAircraftIsNotFound() {
        when(airlines.getAircraftById(1L)).thenReturn(AircraftResponse.builder().id(1L).airlineId(10L).build());
        when(airlines.getAircraftById(2L)).thenReturn(AircraftResponse.builder().id(2L).airlineId(99L).build());

        assertThatCode(() -> ownership.requireOwnedAircraft(OWNER, 1L)).doesNotThrowAnyException();
        assertThatThrownBy(() -> ownership.requireOwnedAircraft(OWNER, 2L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void foreignFlightInstanceIsNotFound() {
        when(flights.getFlightInstanceById(7L)).thenReturn(FlightInstanceResponse.builder().id(7L).airlineId(99L).build());

        assertThatThrownBy(() -> ownership.requireOwnedFlightInstance(OWNER, 7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void systemAdminNeedsNoLookup() {
        ownership.requireOwnedAircraft(ADMIN, 2L);
        ownership.requireAirline(ADMIN, 99L, "Seat map", 3L);

        verify(airlines, never()).getAircraftById(anyLong());
        verify(airlines, never()).getAirlineByOwner(anyLong());
    }
}
