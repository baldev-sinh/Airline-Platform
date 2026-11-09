package com.baldev.airline.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Access-token verification settings shared by the gateway and every service, {@code airline.security.*}.
 *
 * @param jwt         issuer, audience and where the issuer's public keys are published
 * @param serviceAuth whether this service verifies tokens itself ({@code jwt}, default) or trusts the gateway's
 *                    identity headers ({@code headers}, only for tests and private networks you fully control)
 * @param revocation  revoked-token checks (Redis)
 */
@ConfigurationProperties(prefix = "airline.security")
public record TokenSecurityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue ServiceAuth serviceAuth,
        @DefaultValue Revocation revocation) {

    /**
     * @param issuer      required {@code iss}
     * @param audience    required {@code aud}
     * @param jwksUri     JWKS document of the issuer; {@code http://user-service/...} is resolved through Eureka
     * @param jwksMaxAge  keys are re-fetched this often (rotation is picked up sooner, on an unknown {@code kid})
     */
    public record Jwt(
            @DefaultValue("airline-user-service") String issuer,
            @DefaultValue("airline-api") String audience,
            @DefaultValue("http://user-service/.well-known/jwks.json") String jwksUri,
            @DefaultValue("10m") Duration jwksMaxAge) {
    }

    public enum Mode { JWT, HEADERS }

    public record ServiceAuth(@DefaultValue("true") boolean enabled, @DefaultValue("jwt") Mode mode) {
    }

    public record Revocation(@DefaultValue("true") boolean enabled, @DefaultValue("true") boolean failOpen) {
    }
}
