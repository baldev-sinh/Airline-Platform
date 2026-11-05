package com.baldev.airline.gateway.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;

/**
 * @param secret HMAC key shared with user-service. Validated at startup: at least 32 bytes for HS256.
 * @param issuer expected {@code iss} claim; tokens from any other issuer are rejected.
 */
@Validated
@ConfigurationProperties(prefix = "airline.security.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer) {

    public JwtProperties {
        if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("airline.security.jwt.secret must be at least 32 bytes");
        }
    }
}
