package com.baldev.airline.gateway.ratelimit;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * {@code airline.rate-limit.*}: token buckets per client IP, shared by every gateway instance through Redis.
 *
 * @param enabled        master switch
 * @param failOpen       when Redis is unreachable, let requests through (true) or answer 503 (false)
 * @param trustedProxies CIDRs of proxies in front of the gateway (Caddy, nginx, load balancer). Only their
 *                       {@code X-Forwarded-For} entries are believed; anyone else's is ignored, so a client cannot
 *                       pick its own rate-limit key.
 * @param policies       evaluated in order, first match wins; requests matching none are not limited
 */
@Validated
@ConfigurationProperties(prefix = "airline.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("true") boolean failOpen,
        @DefaultValue({"127.0.0.0/8", "::1/128", "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "fc00::/7"})
        List<String> trustedProxies,
        @Valid List<Policy> policies) {

    public RateLimitProperties {
        policies = policies == null ? List.of() : List.copyOf(policies);
    }

    /**
     * A token bucket: up to {@code capacity} requests at once, refilled with {@code refillTokens} every
     * {@code refillPeriod}.
     *
     * @param methods HTTP methods to match; empty matches all
     * @param paths   Spring path patterns, e.g. {@code /auth/login}, {@code /api/**}
     * @param exempt  matching requests are never limited (e.g. provider webhooks, authenticated by signature)
     */
    public record Policy(@NotBlank String name, List<String> methods, @NotEmpty List<String> paths,
                         int capacity, int refillTokens, Duration refillPeriod, boolean exempt) {

        public Policy {
            methods = methods == null ? List.of() : List.copyOf(methods);
            refillPeriod = refillPeriod == null ? Duration.ofMinutes(1) : refillPeriod;
            if (!exempt && (capacity < 1 || refillTokens < 1 || refillPeriod.isZero() || refillPeriod.isNegative())) {
                throw new IllegalArgumentException("Rate limit policy " + name + " needs capacity, refill-tokens and refill-period > 0");
            }
        }

        /** Refill rate in tokens per second. */
        public double ratePerSecond() {
            return refillTokens / (refillPeriod.toMillis() / 1000.0);
        }
    }
}
