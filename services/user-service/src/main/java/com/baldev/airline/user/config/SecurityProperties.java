package com.baldev.airline.user.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "airline")
public record SecurityProperties(@Valid Security security, BootstrapAdmin bootstrapAdmin) {

    public record Security(@Valid @NotNull Jwt jwt) {
    }

    /**
     * @param secret     HMAC key shared with the gateway; at least 32 bytes (validated at startup)
     * @param issuer     {@code iss} claim the gateway requires
     * @param expiration access-token lifetime; keep short (default 1h) since tokens cannot be revoked
     */
    public record Jwt(@NotBlank String secret, @NotBlank String issuer, @DefaultValue("PT1H") Duration expiration) {
        public Jwt {
            if (secret != null && secret.getBytes(StandardCharsets.UTF_8).length < 32) {
                throw new IllegalArgumentException("airline.security.jwt.secret must be at least 32 bytes");
            }
        }
    }

    public record BootstrapAdmin(String email, String password) {
        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
