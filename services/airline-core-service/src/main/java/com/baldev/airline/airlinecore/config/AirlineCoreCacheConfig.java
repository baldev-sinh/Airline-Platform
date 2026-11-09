package com.baldev.airline.airlinecore.config;

import com.baldev.airline.common.cache.CacheSpec;
import com.baldev.airline.airlinecore.payload.response.AircraftResponse;
import com.baldev.airline.airlinecore.payload.response.AirlineDropdownItem;
import com.baldev.airline.airlinecore.payload.response.AirlineResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Airlines are read by every other service on every airline-owner write ("which airline does this user own?"),
 * and by the public search page (dropdown). TTLs live in config-repo/airline-core-service.yaml; every write
 * evicts the affected entries after commit, so status changes such as a ban take effect immediately.
 */
@Configuration(proxyBeanMethods = false)
public class AirlineCoreCacheConfig {

    public static final String AIRLINES = "airlines";
    public static final String AIRLINE_BY_OWNER = "airlineByOwner";
    public static final String AIRLINE_DROPDOWN = "airlineDropdown";
    public static final String AIRCRAFT = "aircraft";

    @Bean
    CacheSpec airlinesCache() {
        return CacheSpec.of(AIRLINES, AirlineResponse.class);
    }

    @Bean
    CacheSpec airlineByOwnerCache() {
        return CacheSpec.of(AIRLINE_BY_OWNER, AirlineResponse.class);
    }

    @Bean
    CacheSpec airlineDropdownCache() {
        return CacheSpec.listOf(AIRLINE_DROPDOWN, AirlineDropdownItem.class);
    }

    @Bean
    CacheSpec aircraftCache() {
        return CacheSpec.of(AIRCRAFT, AircraftResponse.class);
    }
}
