package com.baldev.airline.gateway.security;

import com.baldev.airline.gateway.security.ParityFixtures.Spec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Revocation at the gateway, against a real Redis holding the same keys user-service writes on logout and
 * "sign out everywhere" ({@code airline:auth:revoked-jti:<jti>}, {@code airline:auth:revoked-before:<userId>}).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/api-gateway.yaml",
        "eureka.client.enabled=false",
        "management.server.port=-1",
        "airline.rate-limit.enabled=false"
})
@AutoConfigureMockMvc
class GatewayRevocationParityTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

    static final ParityFixtures.Backend BACKEND = new ParityFixtures.Backend();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        ParityFixtures.routeAllServicesTo(registry, BACKEND.baseUrl());
    }

    @AfterAll
    static void stop() {
        BACKEND.close();
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    StringRedisTemplate redis;

    static final String REVOKED = GatewaySecurityParityTest.problem(401, "Unauthorized", "Access token has been revoked",
            "UNAUTHORIZED", "/api/bookings/9");

    @Test
    void loggedOutTokenIsRejected() throws Exception {
        Spec spec = Spec.valid(70, "ROLE_USER");
        String token = ParityFixtures.token(spec);
        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());

        redis.opsForValue().set("airline:auth:revoked-jti:" + spec.jti(), "1", Duration.ofMinutes(5));

        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(REVOKED, JsonCompareMode.STRICT));
        // Another token of the same user is unaffected.
        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Bearer " + ParityFixtures.token(70, "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void signOutEverywhereRejectsTokensIssuedUpToThatSecond() throws Exception {
        String before = ParityFixtures.token(71, "ROLE_USER");
        redis.opsForValue().set("airline:auth:revoked-before:71", String.valueOf(Instant.now().getEpochSecond()),
                Duration.ofMinutes(16));

        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Bearer " + before))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json(REVOKED, JsonCompareMode.STRICT));

        // A token issued after the revocation point works (sign in again).
        Spec later = Spec.valid(71, "ROLE_USER");
        later = new Spec(later.kid(), later.issuer(), later.audience(), later.subject(), later.roles(),
                Instant.now().plusSeconds(2), later.expiresAt(), later.keys(), later.jti());
        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Bearer " + ParityFixtures.token(later)))
                .andExpect(status().isOk());
    }

    @Test
    void publicRoutesIgnoreRevocation() throws Exception {
        Spec spec = Spec.valid(72, "ROLE_USER");
        redis.opsForValue().set("airline:auth:revoked-jti:" + spec.jti(), "1", Duration.ofMinutes(5));
        mvc.perform(get("/api/flights/search").header(HttpHeaders.AUTHORIZATION, "Bearer " + ParityFixtures.token(spec)))
                .andExpect(status().isOk());
    }
}
