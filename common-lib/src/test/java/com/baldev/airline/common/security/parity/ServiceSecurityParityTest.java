package com.baldev.airline.common.security.parity;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.stream.Stream;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security parity inside a business service, over HTTP only, so it holds unchanged before and after the move to
 * Spring Security (SECURITY_MIGRATION_PLAN.md, table b, "Services" column):
 * <ul>
 *   <li>identity comes only from a verified token; identity headers are never believed;</li>
 *   <li>no token means anonymous, so reads that need no user keep working (public search calls these);</li>
 *   <li>a bad token on /api/** is a 401 with the service error codes;</li>
 *   <li>/internal/** ignores tokens entirely; writes need no CSRF token.</li>
 * </ul>
 */
@SpringBootTest(classes = ServiceParityApp.class, properties = "spring.application.name=parity-service")
@AutoConfigureMockMvc
class ServiceSecurityParityTest {

    static final ServiceParityApp.Jwks JWKS = new ServiceParityApp.Jwks();

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        registry.add("airline.security.jwt.jwks-uri", JWKS::uri);
    }

    @AfterAll
    static void stop() {
        JWKS.close();
    }

    @Autowired
    MockMvc mvc;

    static final String INVALID = """
            {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Invalid access token","code":"INVALID_TOKEN"}""";

    @Test
    void identityComesFromTheToken() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + ServiceParityApp.token(42, "ROLE_AIRLINE_OWNER")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":42,"email":"parity@example.com","roles":["ROLE_AIRLINE_OWNER"]}""", JsonCompareMode.STRICT));
    }

    @Test
    void spoofedIdentityHeadersAreIgnoredWhenATokenIsPresent() throws Exception {
        mvc.perform(get("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ServiceParityApp.token(42, "ROLE_USER"))
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@example.com")
                        .header("X-User-Roles", "ROLE_SYSTEM_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {"id":42,"email":"parity@example.com","roles":["ROLE_USER"]}""", JsonCompareMode.STRICT));
    }

    @Test
    void spoofedIdentityHeadersAloneAreNotAnIdentity() throws Exception {
        mvc.perform(get("/api/me").header("X-User-Id", "1").header("X-User-Roles", "ROLE_SYSTEM_ADMIN"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"detail":"Authentication required","instance":"/api/me","status":401,"title":"Unauthorized","code":"UNAUTHORIZED"}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    void anonymousReadsStillWork() throws Exception {
        // Public flight search makes calls like this one without a token.
        mvc.perform(get("/api/public")).andExpect(status().isOk());
    }

    static Stream<String> invalidTokens() {
        Instant later = Instant.now().plusSeconds(900);
        return Stream.of(
                "not-a-jwt",
                ServiceParityApp.token("42", ServiceParityApp.KID, ServiceParityApp.ISSUER, Instant.now().minusSeconds(120), "ROLE_USER"),
                ServiceParityApp.token("42", ServiceParityApp.KID, "someone-else", later, "ROLE_USER"),
                ServiceParityApp.token("42", "unknown-key", ServiceParityApp.ISSUER, later, "ROLE_USER"),
                ServiceParityApp.token("42", null, ServiceParityApp.ISSUER, later, "ROLE_USER"),
                ServiceParityApp.token("not-a-number", ServiceParityApp.KID, ServiceParityApp.ISSUER, later, "ROLE_USER"));
    }

    @ParameterizedTest(name = "invalid token #{index}")
    @MethodSource("invalidTokens")
    void invalidTokenOnAnyApiPathIs401(String token) throws Exception {
        for (String path : new String[]{"/api/me", "/api/public"}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(content().json(INVALID, JsonCompareMode.STRICT));
        }
    }

    @Test
    void unsupportedAuthorizationSchemeIs401() throws Exception {
        mvc.perform(get("/api/public").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {"type":"about:blank","title":"Unauthorized","status":401,"detail":"Unsupported authorization scheme","code":"INVALID_TOKEN"}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    void writesNeedNoCsrfToken() throws Exception {
        mvc.perform(post("/api/things").header(HttpHeaders.AUTHORIZATION, "Bearer " + ServiceParityApp.token(42, "ROLE_USER")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"createdBy\":42}", JsonCompareMode.STRICT));
        mvc.perform(post("/internal/thing")).andExpect(status().isOk());
    }

    @Test
    void internalEndpointsIgnoreTokens() throws Exception {
        // Feign relays the caller's token to /internal too; an expired one must not fail the internal call.
        mvc.perform(get("/internal/thing").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt")).andExpect(status().isOk());
        mvc.perform(get("/internal/thing").header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + ServiceParityApp.token("42", ServiceParityApp.KID, ServiceParityApp.ISSUER, Instant.now().minusSeconds(3600), "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void noSessionIsCreated() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + ServiceParityApp.token(42, "ROLE_USER")))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }
}
