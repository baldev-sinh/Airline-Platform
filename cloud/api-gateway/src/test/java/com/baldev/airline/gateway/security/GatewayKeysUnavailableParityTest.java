package com.baldev.airline.gateway.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Issuer unreachable and no key cached yet: 503, so clients retry instead of signing the user out. */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/api-gateway.yaml",
        "eureka.client.enabled=false",
        "management.server.port=-1",
        "airline.rate-limit.enabled=false",
        "airline.security.revocation.enabled=false"
})
@AutoConfigureMockMvc
class GatewayKeysUnavailableParityTest {

    static final ParityFixtures.Backend BACKEND = new ParityFixtures.Backend();

    @DynamicPropertySource
    static void backend(DynamicPropertyRegistry registry) {
        BACKEND.jwksAvailable = false;
        ParityFixtures.routeAllServicesTo(registry, BACKEND.baseUrl());
    }

    @AfterAll
    static void stop() {
        BACKEND.close();
    }

    @Autowired
    MockMvc mvc;

    @Test
    void answers503SoClientsRetryInsteadOfSigningOut() throws Exception {
        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Bearer " + ParityFixtures.token(42, "ROLE_USER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().json(GatewaySecurityParityTest.problem(503, "Service Unavailable",
                        "Authentication is temporarily unavailable", "SERVICE_UNAVAILABLE", "/api/bookings/9"),
                        JsonCompareMode.STRICT));
    }

    @Test
    void publicRoutesStillWork() throws Exception {
        mvc.perform(get("/api/flights/search").header(HttpHeaders.AUTHORIZATION, "Bearer " + ParityFixtures.token(42, "ROLE_USER")))
                .andExpect(status().isOk());
    }

    @Test
    void missingTokenIsStill401() throws Exception {
        mvc.perform(get("/api/bookings/9")).andExpect(status().isUnauthorized());
    }
}
