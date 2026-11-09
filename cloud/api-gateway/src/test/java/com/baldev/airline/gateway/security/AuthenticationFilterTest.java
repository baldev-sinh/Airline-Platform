package com.baldev.airline.gateway.security;

import com.baldev.airline.common.security.AccessTokenVerifier;
import com.baldev.airline.common.security.JwksKeyResolver;
import com.baldev.airline.common.security.TokenRevocations;
import com.baldev.airline.common.web.RequestHeaders;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.function.ServerRequest;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationFilterTest {

    private static final String ISSUER = "airline-user-service";
    private static final String AUDIENCE = "airline-api";
    private static final KeyPair KEYS = rsa();

    private final AuthenticationFilter filter = filter(TokenRevocations.NONE);

    @Test
    void stripsSpoofedIdentityHeadersOnPublicRoutes() {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/auth/login");
        raw.addHeader(RequestHeaders.USER_ID, "1");
        raw.addHeader(RequestHeaders.USER_ROLES, "ROLE_SYSTEM_ADMIN");

        ServerRequest result = filter.apply(request(raw));

        assertThat(result.headers().header(RequestHeaders.USER_ID)).isEmpty();
        assertThat(result.headers().header(RequestHeaders.USER_ROLES)).isEmpty();
    }

    @Test
    void replacesSpoofedHeadersWithTokenIdentity() {
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/api/bookings/9");
        raw.addHeader("Authorization", "Bearer " + token(42L, List.of("ROLE_USER"), ISSUER, 3600));
        raw.addHeader(RequestHeaders.USER_ID, "1");
        raw.addHeader(RequestHeaders.USER_ROLES, "ROLE_SYSTEM_ADMIN");

        ServerRequest result = filter.apply(request(raw));

        assertThat(result.headers().header(RequestHeaders.USER_ID)).containsExactly("42");
        assertThat(result.headers().header(RequestHeaders.USER_ROLES)).containsExactly("ROLE_USER");
    }

    @Test
    void rejectsMissingToken() {
        assertStatus(new MockHttpServletRequest("GET", "/api/bookings/9"), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/api/bookings/9");
        raw.addHeader("Authorization", "Bearer " + token(42L, List.of("ROLE_USER"), "someone-else", 3600));
        assertStatus(raw, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsExpiredToken() {
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/api/bookings/9");
        raw.addHeader("Authorization", "Bearer " + token(42L, List.of("ROLE_USER"), ISSUER, -120));
        assertStatus(raw, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsHmacTokenEvenWithAValidLookingPayload() {
        String hmac = Jwts.builder().header().keyId("k1").and().issuer(ISSUER).audience().add(AUDIENCE).and()
                .subject("1").claim("roles", List.of("ROLE_SYSTEM_ADMIN"))
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(new byte[32])).compact();
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/api/bookings/9");
        raw.addHeader("Authorization", "Bearer " + hmac);
        assertStatus(raw, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsRevokedToken() {
        AuthenticationFilter revoking = filter(new TokenRevocations() {
            @Override
            public boolean isRevoked(String tokenId, Long userId, Instant issuedAt) {
                return true;
            }

            @Override
            public void revokeToken(String tokenId, Instant expiresAt) {
            }

            @Override
            public void revokeAllIssuedBefore(Long userId, Instant before, Duration ttl) {
            }
        });
        MockHttpServletRequest raw = new MockHttpServletRequest("GET", "/api/bookings/9");
        raw.addHeader("Authorization", "Bearer " + token(42L, List.of("ROLE_USER"), ISSUER, 3600));
        assertThatThrownBy(() -> revoking.apply(request(raw)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void forbidsCustomerOnAdminRoute() {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/api/airlines/7/approve");
        raw.addHeader("Authorization", "Bearer " + token(42L, List.of("ROLE_USER"), ISSUER, 3600));
        assertStatus(raw, HttpStatus.FORBIDDEN);
    }

    @Test
    void roleMatchingIsExactNotSubstring() {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/api/airlines/7/approve");
        raw.addHeader("Authorization", "Bearer " + token(42L, List.of("ROLE_SYSTEM_ADMIN_TRAINEE"), ISSUER, 3600));
        assertStatus(raw, HttpStatus.FORBIDDEN);
    }

    @Test
    void allowsAdminOnAdminRoute() {
        MockHttpServletRequest raw = new MockHttpServletRequest("POST", "/api/airlines/7/approve");
        raw.addHeader("Authorization", "Bearer " + token(1L, List.of("ROLE_SYSTEM_ADMIN"), ISSUER, 3600));
        assertThat(filter.apply(request(raw)).headers().header(RequestHeaders.USER_ID)).containsExactly("1");
    }

    @Test
    void hidesInternalEndpoints() {
        assertStatus(new MockHttpServletRequest("POST", "/internal/payments"), HttpStatus.NOT_FOUND);
    }

    private void assertStatus(MockHttpServletRequest raw, HttpStatus expected) {
        assertThatThrownBy(() -> filter.apply(request(raw)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(expected));
    }

    private static ServerRequest request(MockHttpServletRequest raw) {
        return ServerRequest.create(raw, List.of());
    }

    private static String token(Long userId, List<String> roles, String issuer, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId("k1").and()
                .id(UUID.randomUUID().toString())
                .audience().add(AUDIENCE).and()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .issuedAt(Date.from(now.minus(1, ChronoUnit.MINUTES)))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .claim("roles", roles)
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static AuthenticationFilter filter(TokenRevocations revocations) {
        return new AuthenticationFilter(new AccessPolicy(), new AccessTokenVerifier(
                JwksKeyResolver.fixed(Map.of("k1", KEYS.getPublic())), revocations, ISSUER, AUDIENCE, Clock.systemUTC()));
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
