package com.baldev.airline.pricing.config;

import com.baldev.airline.common.cache.CacheSpec;
import com.baldev.airline.pricing.payload.response.FareResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Fares are read on every flight page and every checkout. Only lookups by id are cached: prices change by
 * explicit edits, which evict the entry after commit. TTLs live in config-repo/pricing-service.yaml.
 */
@Configuration(proxyBeanMethods = false)
public class PricingCacheConfig {

    public static final String FARES = "fares";

    @Bean
    CacheSpec faresCache() {
        return CacheSpec.of(FARES, FareResponse.class);
    }
}
