package com.baldev.airline.common.security;

import com.baldev.airline.common.web.RequestHeaders;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    private static final KeyPair KEYS = rsa();
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(new AccessTokenVerifier(
            JwksKeyResolver.fixed(Map.of("k1", KEYS.getPublic())), TokenRevocations.NONE, "iss", "aud", Clock.systemUTC()));

    @Test
    void tokenIdentityReplacesForgedHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings/1");
        request.addHeader("Authorization", "Bearer " + token("aud"));
        request.addHeader(RequestHeaders.USER_ID, "1");
        request.addHeader(RequestHeaders.USER_ROLES, "ROLE_SYSTEM_ADMIN");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest seen = (HttpServletRequest) chain.getRequest();
        assertThat(seen.getHeader(RequestHeaders.USER_ID)).isEqualTo("42");
        assertThat(seen.getHeader("x-user-roles")).isEqualTo("ROLE_USER");
        assertThat(java.util.Collections.list(seen.getHeaders(RequestHeaders.USER_ID))).containsExactly("42");
    }

    @Test
    void requestWithoutTokenIsAnonymousEvenWithForgedHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings/1");
        request.addHeader(RequestHeaders.USER_ID, "1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(((HttpServletRequest) chain.getRequest()).getHeader(RequestHeaders.USER_ID)).isNull();
        assertThat(java.util.Collections.list(((HttpServletRequest) chain.getRequest()).getHeaderNames()))
                .doesNotContain(RequestHeaders.USER_ID);
    }

    @Test
    void invalidTokenIs401WithProblemDetails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings/1");
        request.addHeader("Authorization", "Bearer " + token("someone-else"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("INVALID_TOKEN");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void unreachableIssuerIs503NotAnonymous() throws Exception {
        JwtAuthenticationFilter noKeys = new JwtAuthenticationFilter(new AccessTokenVerifier(
                new JwksKeyResolver(() -> {
                    throw new IllegalStateException("user-service down");
                }, Clock.systemUTC(), java.time.Duration.ofMinutes(10), java.time.Duration.ZERO),
                TokenRevocations.NONE, "iss", "aud", Clock.systemUTC()));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/bookings/1");
        request.addHeader("Authorization", "Bearer " + token("aud"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        noKeys.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(503);
    }

    @Test
    void internalEndpointsAreNotFiltered() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/users/1");
        request.addHeader(RequestHeaders.USER_ID, "1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static String token(String audience) {
        return Jwts.builder().header().keyId("k1").and()
                .id(UUID.randomUUID().toString()).issuer("iss").audience().add(audience).and().subject("42")
                .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(600)))
                .claim("roles", List.of("ROLE_USER"))
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private static KeyPair rsa() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
