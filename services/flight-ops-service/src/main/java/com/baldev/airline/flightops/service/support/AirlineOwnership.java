package com.baldev.airline.flightops.service.support;

import com.baldev.airline.flightops.client.AirlineClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Resolves the airline owned by the calling user; every write is scoped to that airline. */
@Component
@RequiredArgsConstructor
public class AirlineOwnership {

    private final AirlineClient airlineClient;

    /** @return the id of the airline owned by {@code userId}; 404 if the user owns no airline. */
    public Long airlineIdOf(Long userId) {
        return airlineClient.getCallerAirline().getId();
    }
}
