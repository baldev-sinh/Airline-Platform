package com.baldev.airline.common.security.resource;

import com.baldev.airline.common.security.TokenRevocations;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;

import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccessTokenDecodersTest {

    static final KeyPair KEYS = rsa();
    static final KeyPair OTHER = rsa();
    static final String KID = "k1";

    private final JwtDecoder decoder = AccessTokenDecoders.fixed(Map.of(KID, (RSAPublicKey) KEYS.getPublic()),
            AccessTokenValidators.of("airline-user-service", "airline-api", TokenRevocations.NONE, Clock.systemUTC()));

    @Test
    void acceptsAValidTokenAndExposesItsClaims() {
        Jwt jwt = decoder.decode(token(b -> b));
        assertThat(jwt.getSubject()).isEqualTo("42");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ROLE_USER");
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void allowsThirtySecondsOfClockSkew() {
        // Realistic tokens: issued 15 minutes ago, expired 10 s (inside the skew) or 60 s (outside) ago.
        Date issued = Date.from(Instant.now().minusSeconds(900));
        assertThat(decoder.decode(token(b -> b.issuedAt(issued).expiration(Date.from(Instant.now().minusSeconds(10)))))).isNotNull();
        assertThatThrownBy(() -> decoder.decode(token(b -> b.issuedAt(issued).expiration(Date.from(Instant.now().minusSeconds(60))))))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void requiresExpiry() {
        assertThatThrownBy(() -> decoder.decode(token(b -> b.expiration(null)))).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void requiresIssuerAndAudience() {
        assertThatThrownBy(() -> decoder.decode(token(b -> b.issuer("someone-else")))).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(token(b -> b.audience().single("another-api"))))
                .isInstanceOf(JwtValidationException.class);
    }

    @Test
    void requiresANumericSubject() {
        assertThatThrownBy(() -> decoder.decode(token(b -> b.subject("admin")))).isInstanceOf(JwtValidationException.class);
        assertThatThrownBy(() -> decoder.decode(token(b -> b.subject("0")))).isInstanceOf(JwtValidationException.class);
    }

    @Test
    void rejectsBadSignaturesAndAlgorithms() {
        assertThatThrownBy(() -> decoder.decode(signedBy(OTHER, KID))).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(signedBy(KEYS, "unknown"))).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(signedBy(KEYS, null))).as("no kid").isInstanceOf(BadJwtException.class);
        String hmac = Jwts.builder().header().keyId(KID).and().issuer("airline-user-service")
                .audience().add("airline-api").and().subject("1").expiration(Date.from(Instant.now().plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(new byte[32])).compact();
        assertThatThrownBy(() -> decoder.decode(hmac)).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode(unsigned())).isInstanceOf(BadJwtException.class);
        assertThatThrownBy(() -> decoder.decode("not-a-jwt")).isInstanceOf(BadJwtException.class);
    }

    @Test
    void revokedTokensFailWithTheirOwnErrorCode() {
        TokenRevocations everything = new TokenRevocations() {
            @Override
            public boolean isRevoked(String tokenId, Long userId, Instant issuedAt) {
                return true;
            }

            @Override
            public void revokeToken(String tokenId, Instant expiresAt) {
            }

            @Override
            public void revokeAllIssuedBefore(Long userId, Instant before, Duration lifetime) {
            }
        };
        JwtDecoder revoking = AccessTokenDecoders.fixed(Map.of(KID, (RSAPublicKey) KEYS.getPublic()),
                AccessTokenValidators.of("airline-user-service", "airline-api", everything, Clock.systemUTC()));
        assertThatThrownBy(() -> revoking.decode(token(b -> b)))
                .isInstanceOfSatisfying(JwtValidationException.class, e -> assertThat(e.getErrors())
                        .extracting(OAuth2Error::getErrorCode).containsExactly(AuthFailures.TOKEN_REVOKED));
    }

    @Test
    void unreachableIssuerWithNothingCachedIsNotABadToken() {
        // Not a BadJwtException: Spring turns it into an AuthenticationServiceException, which we answer with 503.
        JwtDecoder remote = AccessTokenDecoders.remote("http://127.0.0.1:1/.well-known/jwks.json", Duration.ofMinutes(10),
                AccessTokenDecoders.jwksClient(UnaryOperator.identity()),
                AccessTokenValidators.of("airline-user-service", "airline-api", TokenRevocations.NONE, Clock.systemUTC()));
        assertThatThrownBy(() -> remote.decode(token(b -> b)))
                .isInstanceOf(JwtException.class)
                .isNotInstanceOf(BadJwtException.class);
    }

    @Test
    void unknownKeyRightAfterAFetchIsABadTokenNotAnOutage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            byte[] body = jwks().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            JwtDecoder remote = AccessTokenDecoders.remote("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                    Duration.ofMinutes(10), AccessTokenDecoders.jwksClient(UnaryOperator.identity()),
                    AccessTokenValidators.of("airline-user-service", "airline-api", TokenRevocations.NONE, Clock.systemUTC()));
            assertThat(remote.decode(token(b -> b))).isNotNull();
            // Within the 30 s refetch limit: the key is simply unknown (401), the issuer is fine.
            assertThatThrownBy(() -> remote.decode(signedBy(KEYS, "unknown"))).isInstanceOf(BadJwtException.class);
            assertThatThrownBy(() -> remote.decode(signedBy(KEYS, "unknown-2"))).isInstanceOf(BadJwtException.class);
            assertThat(remote.decode(token(b -> b))).as("known keys still work").isNotNull();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cachedKeysSurviveAnIssuerOutage() throws Exception {
        boolean[] up = {true};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/jwks", exchange -> {
            byte[] body = (up[0] ? jwks() : "down").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(up[0] ? 200 : 503, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
        try {
            JwtDecoder remote = AccessTokenDecoders.remote("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks",
                    Duration.ofMinutes(10), AccessTokenDecoders.jwksClient(UnaryOperator.identity()),
                    AccessTokenValidators.of("airline-user-service", "airline-api", TokenRevocations.NONE, Clock.systemUTC()));
            assertThat(remote.decode(token(b -> b))).isNotNull();
            up[0] = false;
            assertThat(remote.decode(token(b -> b))).isNotNull();
            // An unknown key id during the outage is a bad token (401), not "unavailable" (503): keys are cached.
            assertThatThrownBy(() -> remote.decode(signedBy(KEYS, "rotated"))).isInstanceOf(BadJwtException.class);
        } finally {
            server.stop(0);
        }
    }

    // ------------------------------------------------------------------ helpers

    static String token(UnaryOperator<JwtBuilder> change) {
        JwtBuilder builder = Jwts.builder().header().keyId(KID).and()
                .id(UUID.randomUUID().toString())
                .issuer("airline-user-service")
                .audience().add("airline-api").and()
                .subject("42")
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("roles", List.of("ROLE_USER"));
        return change.apply(builder).signWith(KEYS.getPrivate(), Jwts.SIG.RS256).compact();
    }

    static String signedBy(KeyPair keys, String kid) {
        var builder = Jwts.builder();
        if (kid != null) {
            builder.header().keyId(kid).and();
        }
        return builder.issuer("airline-user-service").audience().add("airline-api").and().subject("42")
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(keys.getPrivate(), Jwts.SIG.RS256).compact();
    }

    static String unsigned() {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        long exp = Instant.now().plusSeconds(600).getEpochSecond();
        return b64.encodeToString(("{\"alg\":\"none\",\"kid\":\"" + KID + "\"}").getBytes(StandardCharsets.UTF_8)) + "."
                + b64.encodeToString(("{\"iss\":\"airline-user-service\",\"aud\":\"airline-api\",\"sub\":\"1\",\"exp\":" + exp + "}")
                .getBytes(StandardCharsets.UTF_8)) + ".";
    }

    static String jwks() {
        RSAPublicKey key = (RSAPublicKey) KEYS.getPublic();
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        return "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"" + KID + "\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\""
                + b64.encodeToString(unsignedBytes(key.getModulus())) + "\",\"e\":\""
                + b64.encodeToString(unsignedBytes(key.getPublicExponent())) + "\"}]}";
    }

    private static byte[] unsignedBytes(BigInteger value) {
        byte[] bytes = value.toByteArray();
        return bytes[0] == 0 ? Arrays.copyOfRange(bytes, 1, bytes.length) : bytes;
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
