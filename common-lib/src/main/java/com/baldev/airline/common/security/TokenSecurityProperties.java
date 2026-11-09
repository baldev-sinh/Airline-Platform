package com.baldev.airline.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Access-token verification settings shared by the gateway and every service, {@code airline.security.*}.
 *
 * @param jwt         issuer, audience and where the issuer's public keys are published
 * @param serviceAuth whether common-lib sets this service up as a resource server (the gateway turns it off and
 *                    declares its own chain)
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

    public record ServiceAuth(@DefaultValue("true") boolean enabled) {
    }

    public record Revocation(@DefaultValue("true") boolean enabled, @DefaultValue("true") boolean failOpen) {
    }
}
