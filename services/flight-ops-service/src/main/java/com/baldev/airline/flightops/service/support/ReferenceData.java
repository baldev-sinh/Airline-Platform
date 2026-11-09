package com.baldev.airline.flightops.service.support;

import com.baldev.airline.common.exception.ApiException;
import com.baldev.airline.flightops.client.dto.AircraftResponse;
import com.baldev.airline.flightops.client.dto.AirlineResponse;
import com.baldev.airline.flightops.client.dto.AirportResponse;
import com.baldev.airline.flightops.client.AirlineClient;
import com.baldev.airline.flightops.client.LocationClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Airline, aircraft and airport details for flight responses, behind one circuit breaker per downstream service.
 *
 * <p>These details only decorate a response (names, logos, cities); the flight itself is ours. So when
 * airline-core-service or location-service is failing, the breaker opens and callers get a placeholder holding
 * just the id ({@link Ref#available()} false), instead of a 500 for the whole search page; the response is
 * then marked {@code partial} and never cached. Client errors
 * (404, 409, ...) are answers, not outages: they are rethrown and do not count towards opening the breaker.
 *
 * <p>Breakers are configured in config-repo/flight-ops-service.yaml (resilience4j.circuitbreaker.instances).
 * They run on the calling thread (no time limiter thread pool), so the Feign token relay and tracing context
 * still work; Feign's own timeouts bound each call.
 */
@Slf4j
@Component
public class ReferenceData {

    private final AirlineClient airlineClient;
    private final LocationClient locationClient;
    private final CircuitBreaker airlineCore;
    private final CircuitBreaker location;

    public ReferenceData(AirlineClient airlineClient, LocationClient locationClient,
                         CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.airlineClient = airlineClient;
        this.locationClient = locationClient;
        this.airlineCore = circuitBreakers.create("airline-core-service");
        this.location = circuitBreakers.create("location-service");
    }

    /** A looked-up value; {@code available} is false when it is only a placeholder holding the id. */
    public record Ref<T>(T value, boolean available) {
    }

    public Ref<AirlineResponse> airline(Long id) {
        return call(airlineCore, () -> airlineClient.getAirlineById(id), () -> {
            AirlineResponse placeholder = new AirlineResponse();
            placeholder.setId(id);
            return placeholder;
        }, "airline", id);
    }

    public Ref<AircraftResponse> aircraft(Long id) {
        return call(airlineCore, () -> airlineClient.getAircraftById(id), () -> {
            AircraftResponse placeholder = new AircraftResponse();
            placeholder.setId(id);
            return placeholder;
        }, "aircraft", id);
    }

    public Ref<AirportResponse> airport(Long id) {
        return call(location, () -> locationClient.getAirportById(id), () -> {
            AirportResponse placeholder = new AirportResponse();
            placeholder.setId(id);
            return placeholder;
        }, "airport", id);
    }

    private <T> Ref<T> call(CircuitBreaker breaker, Supplier<T> remote, Supplier<T> placeholder, String what, Long id) {
        return breaker.run(() -> new Ref<>(remote.get(), true), failure -> {
            if (failure instanceof ApiException api && api.getStatus().is4xxClientError()) {
                throw api;
            }
            log.warn("Serving {} {} without details: {}", what, id, failure.toString());
            return new Ref<>(placeholder.get(), false);
        });
    }
}
