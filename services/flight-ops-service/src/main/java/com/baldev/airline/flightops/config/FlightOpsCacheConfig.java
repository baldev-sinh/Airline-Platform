package com.baldev.airline.flightops.config;

import com.baldev.airline.common.cache.CacheSpec;
import com.baldev.airline.flightops.payload.response.FlightResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A flight response is assembled from several services (airline, airports, aircraft), so caching it saves
 * those remote calls for every fare and seat page. TTLs live in config-repo/flight-ops-service.yaml.
 */
@Configuration(proxyBeanMethods = false)
public class FlightOpsCacheConfig {

    public static final String FLIGHTS = "flights";

    @Bean
    CacheSpec flightsCache() {
        return CacheSpec.of(FLIGHTS, FlightResponse.class);
    }
}
