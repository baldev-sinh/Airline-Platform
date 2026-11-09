package com.baldev.airline.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.Map;

/**
 * Distributed (Redis) cache settings, {@code airline.cache.*}.
 *
 * @param enabled    turn caching off without a code change (every lookup then goes to the database)
 * @param defaultTtl lifetime of an entry when its cache has no entry in {@code ttl}
 * @param ttl        per-cache lifetimes; these caches are also created (and get metrics) at startup
 * @param keyVersion bump when a cached DTO changes shape incompatibly: new keys are used and old entries expire
 */
@ConfigurationProperties(prefix = "airline.cache")
public record AirlineCacheProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("10m") Duration defaultTtl,
        Map<String, Duration> ttl,
        @DefaultValue("1") String keyVersion) {

    public AirlineCacheProperties {
        ttl = ttl == null ? Map.of() : Map.copyOf(ttl);
    }
}
