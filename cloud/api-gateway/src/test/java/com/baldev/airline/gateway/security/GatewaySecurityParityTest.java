package com.baldev.airline.gateway.security;

import com.baldev.airline.gateway.security.ParityFixtures.Spec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security parity at the API gateway: every rule of the access table and every edge behaviour, asserted only
 * through HTTP (status, exact problem-details body, response headers, and what reaches the backend). Written
 * against the hand-written AccessPolicy/AuthenticationFilter; it must pass unchanged after the move to Spring
 * Security. See SECURITY_MIGRATION_PLAN.md, tables a and b.
 */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/api-gateway.yaml",
        "eureka.client.enabled=false",
        "management.server.port=-1",
        "airline.rate-limit.enabled=false",
        "airline.security.revocation.enabled=false",
        "airline.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
class GatewaySecurityParityTest {

    static final ParityFixtures.Backend BACKEND = new ParityFixtures.Backend();

    @DynamicPropertySource
    static void backend(DynamicPropertyRegistry registry) {
        ParityFixtures.routeAllServicesTo(registry, BACKEND.baseUrl());
    }

    @AfterAll
    static void stop() {
        BACKEND.close();
    }

    @Autowired
    MockMvc mvc;

    @BeforeEach
    void reset() {
        BACKEND.forget();
    }

    static final String USER = ParityFixtures.token(42, "ROLE_USER");
    static final String OWNER = ParityFixtures.token(43, "ROLE_AIRLINE_OWNER");
    static final String ADMIN = ParityFixtures.token(1, "ROLE_SYSTEM_ADMIN");

    // ================================================================== table a: every access rule

    /**
     * Each row: method, path, access level (the rule that must match first). For every level the test checks
     * all four callers (anonymous, customer, airline owner, system admin). The rows are AccessPolicyTest's, plus
     * one for each rule that test did not cover.
     */
    @ParameterizedTest(name = "{0} {1} is {2}")
    @CsvSource({
            // never exposed (routed paths, so the deny comes from the security layer, not from missing routes)
            "POST,   /api/fares/batch-by-ids,               DENY",
            // public
            "POST,   /auth/login,                           PUBLIC",
            "POST,   /auth/signup,                          PUBLIC",
            "POST,   /auth/refresh,                         PUBLIC",
            "POST,   /auth/logout,                          PUBLIC",
            "GET,    /api/flights/search,                   PUBLIC",
            "GET,    /api/cities/4,                         PUBLIC",
            "GET,    /api/airports/12,                      PUBLIC",
            "GET,    /api/airlines/dropdown,                PUBLIC",
            "POST,   /api/payments/webhooks/razorpay,       PUBLIC",
            // system administration
            "POST,   /api/cities,                           ADMIN",
            "PUT,    /api/cities/4,                         ADMIN",
            "DELETE, /api/airports/3,                       ADMIN",
            "PATCH,  /api/airports/3,                       ADMIN",
            "GET,    /api/airlines,                         ADMIN",
            "POST,   /api/airlines/7/approve,               ADMIN",
            "POST,   /api/airlines/7/suspend,               ADMIN",
            "POST,   /api/airlines/7/ban,                   ADMIN",
            "GET,    /api/users,                            ADMIN",
            "GET,    /api/users/42,                         ADMIN",
            "DELETE, /api/users/42,                         ADMIN",
            "GET,    /api/payments,                         ADMIN",
            "DELETE, /api/bookings/9,                       ADMIN",
            // signed in, ahead of the admin-only /api/users/**
            "GET,    /api/users/profile,                    AUTHENTICATED",
            "POST,   /api/users/me/logout-all,              AUTHENTICATED",
            // airline back office
            "GET,    /api/bookings/airline,                 AIRLINE_OWNER",
            "POST,   /api/airlines,                         AIRLINE_OWNER",
            "PUT,    /api/airlines,                         AIRLINE_OWNER",
            "DELETE, /api/airlines/7,                       AIRLINE_OWNER",
            "POST,   /api/fares/search,                     AUTHENTICATED",
            "POST,   /api/aircrafts,                        AIRLINE_OWNER",
            "PUT,    /api/flights/5,                        AIRLINE_OWNER",
            "PATCH,  /api/flight-instances/5,               AIRLINE_OWNER",
            "DELETE, /api/flight-schedules/5,               AIRLINE_OWNER",
            "PUT,    /api/fares/5,                          AIRLINE_OWNER",
            "POST,   /api/fare-rules,                       AIRLINE_OWNER",
            "POST,   /api/baggage-policies,                 AIRLINE_OWNER",
            "POST,   /api/cabin-classes,                    AIRLINE_OWNER",
            "DELETE, /api/seat-maps/5,                      AIRLINE_OWNER",
            "PUT,    /api/flight-instance-cabins/5,         AIRLINE_OWNER",
            "POST,   /api/meals,                            AIRLINE_OWNER",
            "POST,   /api/ancillaries,                      AIRLINE_OWNER",
            "POST,   /api/insurance-coverages,              AIRLINE_OWNER",
            "POST,   /api/flight-meals,                     AIRLINE_OWNER",
            "POST,   /api/flight-cabin-ancillaries,         AIRLINE_OWNER",
            // reads of the airline-managed catalogue, and everything else under /api: signed in
            "GET,    /api/fares/5,                          AUTHENTICATED",
            "GET,    /api/seat-maps/5,                      AUTHENTICATED",
            "GET,    /api/seats,                            AUTHENTICATED",
            "POST,   /api/bookings,                         AUTHENTICATED",
            "GET,    /api/bookings/9,                       AUTHENTICATED",
            "PATCH,  /api/bookings/9/cancel,                AUTHENTICATED",
            "POST,   /api/payments/verify,                  AUTHENTICATED",
            "GET,    /api/notifications,                    AUTHENTICATED",
            "POST,   /api/notifications/3/read,             AUTHENTICATED",
            "GET,    /api/airlines/7,                       AUTHENTICATED",
            "GET,    /api/users/me,                         ADMIN",
    })
    void accessRule(String method, String path, String access) throws Exception {
        HttpMethod m = HttpMethod.valueOf(method);
        switch (access) {
            case "DENY" -> {
                for (String token : new String[]{null, USER, OWNER, ADMIN}) {
                    expectNotFound(call(m, path, token), path);
                }
            }
            case "PUBLIC" -> {
                expectProxied(call(m, path, null));
                expectProxied(call(m, path, USER));
                // The token is not even looked at: an expired or garbage one does not stop a public call.
                expectProxied(call(m, path, "garbage"));
            }
            case "AUTHENTICATED" -> {
                expectUnauthorized(call(m, path, null), path, INVALID);
                expectProxied(call(m, path, USER));
                expectProxied(call(m, path, OWNER));
                expectProxied(call(m, path, ADMIN));
            }
            case "AIRLINE_OWNER" -> {
                expectUnauthorized(call(m, path, null), path, INVALID);
                expectForbidden(call(m, path, USER), path);
                expectProxied(call(m, path, OWNER));
                expectProxied(call(m, path, ADMIN));
            }
            case "ADMIN" -> {
                expectUnauthorized(call(m, path, null), path, INVALID);
                expectForbidden(call(m, path, USER), path);
                expectForbidden(call(m, path, OWNER), path);
                expectProxied(call(m, path, ADMIN));
            }
            default -> throw new IllegalArgumentException(access);
        }
    }

    /** Paths no route serves: 404 for everyone, and nothing reaches a backend. */
    @ParameterizedTest(name = "{0} {1} is not reachable")
    @CsvSource({
            "GET,    /internal/users/1",
            "POST,   /internal/payments",
            "GET,    /actuator/env",
            "GET,    /actuator/health",
            "GET,    /.well-known/jwks.json",
            "GET,    /unknown/path",
            "DELETE, /auth/login",
            "GET,    /auth/login",
    })
    void unroutedPathsAreNotFound(String method, String path) throws Exception {
        for (String token : new String[]{null, USER, ADMIN}) {
            call(HttpMethod.valueOf(method), path, token)
                    .andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        }
        assertThat(BACKEND.headersOf(method, path)).as("nothing proxied").isNull();
    }

    @Test
    void roleMatchingIsExactNotSubstring() throws Exception {
        String trainee = ParityFixtures.token(5, "ROLE_SYSTEM_ADMIN_TRAINEE");
        expectForbidden(call(HttpMethod.POST, "/api/airlines/7/approve", trainee), "/api/airlines/7/approve");
        String ownerish = ParityFixtures.token(5, "ROLE_AIRLINE_OWNER_PENDING");
        expectForbidden(call(HttpMethod.POST, "/api/flights", ownerish), "/api/flights");
    }

    // ================================================================== table b: token validation

    static Stream<String> rejectedTokens() {
        Spec valid = Spec.valid(42, "ROLE_USER");
        return Stream.of(
                "not-a-jwt",
                ParityFixtures.token(valid.expiresAt(Instant.now().minusSeconds(120))),
                ParityFixtures.token(valid.issuer("someone-else")),
                ParityFixtures.token(valid.audience("another-api")),
                ParityFixtures.token(valid.signedBy(ParityFixtures.OTHER_KEYS)),
                ParityFixtures.token(valid.kid("unknown-key")),
                ParityFixtures.token(valid.kid(null)),
                ParityFixtures.token(valid.subject("not-a-number")),
                ParityFixtures.hmacToken(),
                ParityFixtures.unsignedToken());
    }

    @ParameterizedTest(name = "rejected token #{index}")
    @MethodSource("rejectedTokens")
    void rejectsInvalidTokens(String token) throws Exception {
        expectUnauthorized(call(HttpMethod.GET, "/api/bookings/9", token), "/api/bookings/9", INVALID);
        assertThat(BACKEND.headersOf("GET", "/api/bookings/9")).as("nothing proxied").isNull();
    }

    @Test
    void acceptsTokenWithinClockSkew() throws Exception {
        // Expired 10 s ago: inside the 30 s allowance.
        String token = ParityFixtures.token(Spec.valid(42, "ROLE_USER").expiresAt(Instant.now().minusSeconds(10)));
        expectProxied(call(HttpMethod.GET, "/api/bookings/9", token));
    }

    @Test
    void nonBearerSchemeIsTreatedAsMissing() throws Exception {
        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(problem(401, "Unauthorized", INVALID, "UNAUTHORIZED", "/api/bookings/9"),
                        JsonCompareMode.STRICT));
    }

    // ================================================================== table b: identity headers

    @Test
    void spoofedIdentityHeadersNeverReachTheBackend() throws Exception {
        mvc.perform(get("/api/bookings/9")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + USER)
                        .header("X-User-Id", "1")
                        .header("X-User-Email", "admin@example.com")
                        .header("X-User-Roles", "ROLE_SYSTEM_ADMIN"))
                .andExpect(status().isOk());
        Map<String, List<String>> seen = BACKEND.headersOf("GET", "/api/bookings/9");
        assertThat(seen.get("authorization")).containsExactly("Bearer " + USER);
        // Either no identity header at all, or the token's own identity: never the client's values.
        assertThat(seen.getOrDefault("x-user-id", List.of("42"))).containsExactly("42");
        assertThat(seen.getOrDefault("x-user-email", List.of("parity@example.com"))).containsExactly("parity@example.com");
        assertThat(seen.getOrDefault("x-user-roles", List.of("ROLE_USER"))).containsExactly("ROLE_USER");
    }

    @Test
    void spoofedIdentityHeadersAreStrippedOnPublicRoutes() throws Exception {
        mvc.perform(get("/api/flights/search")
                        .header("X-User-Id", "1")
                        .header("X-User-Roles", "ROLE_SYSTEM_ADMIN"))
                .andExpect(status().isOk());
        Map<String, List<String>> seen = BACKEND.headersOf("GET", "/api/flights/search");
        assertThat(seen).doesNotContainKeys("x-user-id", "x-user-email", "x-user-roles");
    }

    @Test
    void publicRoutesForwardTheAuthorizationHeaderUntouched() throws Exception {
        // user-service's /auth/logout reads it to revoke the access token.
        mvc.perform(post("/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer " + USER)).andExpect(status().isOk());
        assertThat(BACKEND.headersOf("POST", "/auth/logout").get("authorization")).containsExactly("Bearer " + USER);
    }

    // ================================================================== table b: edge headers and CORS

    @Test
    void securityHeadersOnEveryKindOfResponse() throws Exception {
        for (ResultActions result : List.of(
                call(HttpMethod.GET, "/api/bookings/9", USER),          // proxied
                call(HttpMethod.GET, "/api/bookings/9", null),          // 401
                call(HttpMethod.GET, "/api/users", USER),               // 403
                call(HttpMethod.POST, "/api/fares/batch-by-ids", null), // 404 (denied)
                call(HttpMethod.GET, "/unknown/path", null))) {         // 404 (no route)
            result.andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().string("X-Frame-Options", "DENY"))
                    .andExpect(header().string("Referrer-Policy", "no-referrer"))
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
    }

    @Test
    void corsPreflightFromAllowedOriginNeedsNoToken() throws Exception {
        mvc.perform(options("/api/bookings/9")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "authorization, content-type"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Retry-After, X-RateLimit-Remaining"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        assertThat(BACKEND.headersOf("OPTIONS", "/api/bookings/9")).as("preflight not proxied").isNull();
    }

    @Test
    void corsPreflightFromOtherOriginIsRejected() throws Exception {
        mvc.perform(options("/api/bookings/9")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void corsHeadersAreOnErrorResponsesSoTheBrowserCanReadThem() throws Exception {
        // The web app must see the 401 to refresh its token; without these headers the browser hides it.
        mvc.perform(get("/api/bookings/9").header(HttpHeaders.ORIGIN, "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location, Retry-After, X-RateLimit-Remaining"));
        mvc.perform(get("/api/users").header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + USER))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void noSessionIsCreated() throws Exception {
        MockHttpServletResponse response = call(HttpMethod.GET, "/api/bookings/9", USER).andReturn().getResponse();
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    // ================================================================== helpers

    static final String INVALID = "Missing or invalid access token";

    ResultActions call(HttpMethod method, String path, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            builder.contentType("application/json").content("{}");
        }
        return mvc.perform(builder);
    }

    static void expectProxied(ResultActions result) throws Exception {
        result.andExpect(status().isOk()).andExpect(content().json("{\"proxied\":true}", JsonCompareMode.STRICT));
    }

    static void expectUnauthorized(ResultActions result, String path, String detail) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().json(problem(401, "Unauthorized", detail, "UNAUTHORIZED", path), JsonCompareMode.STRICT));
    }

    static void expectForbidden(ResultActions result, String path) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().json(problem(403, "Forbidden", "Insufficient permissions", "FORBIDDEN", path),
                        JsonCompareMode.STRICT));
    }

    static void expectNotFound(ResultActions result, String path) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().json(problem(404, "Not Found", "Not found", "NOT_FOUND", path), JsonCompareMode.STRICT));
    }

    static String problem(int status, String title, String detail, String code, String instance) {
        return "{\"status\":" + status + ",\"title\":\"" + title + "\",\"detail\":\"" + detail
                + "\",\"code\":\"" + code + "\",\"instance\":\"" + instance + "\"}";
    }
}
