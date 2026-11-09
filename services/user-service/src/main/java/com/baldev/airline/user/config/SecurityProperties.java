package com.baldev.airline.user.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "airline")
public record SecurityProperties(@Valid @NotNull Security security, BootstrapAdmin bootstrapAdmin) {

    public record Security(@Valid @NotNull Jwt jwt, @Valid @NotNull Signing signing, @Valid @DefaultValue Tokens tokens) {
    }

    /**
     * @param issuer   {@code iss} of issued tokens; the gateway and services require it
     * @param audience {@code aud} of issued tokens; the gateway and services require it
     */
    public record Jwt(@NotBlank String issuer, @DefaultValue("airline-api") String audience) {
    }

    /**
     * RS256 signing key. Only user-service holds the private key; everyone else verifies with the public keys
     * published at {@code /.well-known/jwks.json}.
     *
     * @param privateKey           PKCS#8 PEM ({@code JWT_PRIVATE_KEY}); literal {@code \n} sequences are accepted
     * @param keyId                {@code kid} of the current key; derived from the key (RFC 7638 thumbprint) if empty
     * @param previousKeys         public keys of retired signing keys, still published until their tokens expire
     * @param generateEphemeralKey local development only: generate a key at startup when none is configured
     *                             (tokens then stop working at every restart)
     */
    public record Signing(String privateKey, String keyId, List<PublishedKey> previousKeys,
                          @DefaultValue("false") boolean generateEphemeralKey) {
        public Signing {
            previousKeys = previousKeys == null ? List.of() : List.copyOf(previousKeys);
        }
    }

    /** @param publicKey X.509 (SubjectPublicKeyInfo) PEM */
    public record PublishedKey(@NotBlank String keyId, @NotBlank String publicKey) {
    }

    /**
     * @param accessTokenTtl  short: an access token cannot be recalled except through the revocation list
     * @param refreshTokenTtl a refresh token is single-use and rotated on every refresh
     */
    public record Tokens(@DefaultValue("15m") Duration accessTokenTtl, @DefaultValue("14d") Duration refreshTokenTtl) {
    }

    public record BootstrapAdmin(String email, String password) {
        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
