package com.baldev.airline.location.config;

import com.baldev.airline.common.cache.CacheSpec;
import com.baldev.airline.location.payload.response.AirportResponse;
import com.baldev.airline.location.payload.response.CityResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reference data read on every search and booking, changed rarely by administrators: the best cache candidates
 * on the platform. TTLs live in config-repo/location-service.yaml.
 */
@Configuration(proxyBeanMethods = false)
public class LocationCacheConfig {

    public static final String AIRPORTS = "airports";
    public static final String ALL_AIRPORTS = "allAirports";
    public static final String AIRPORTS_BY_CITY = "airportsByCity";
    public static final String CITIES = "cities";

    @Bean
    CacheSpec airportsCache() {
        return CacheSpec.of(AIRPORTS, AirportResponse.class);
    }

    @Bean
    CacheSpec allAirportsCache() {
        return CacheSpec.listOf(ALL_AIRPORTS, AirportResponse.class);
    }

    @Bean
    CacheSpec airportsByCityCache() {
        return CacheSpec.listOf(AIRPORTS_BY_CITY, AirportResponse.class);
    }

    @Bean
    CacheSpec citiesCache() {
        return CacheSpec.of(CITIES, CityResponse.class);
    }
}
