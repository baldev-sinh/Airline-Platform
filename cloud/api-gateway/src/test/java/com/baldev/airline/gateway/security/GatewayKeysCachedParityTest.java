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

/** Issuer unreachable after keys were cached: tokens keep working on the cached keys. */
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/api-gateway.yaml",
        "eureka.client.enabled=false",
        "management.server.port=-1",
        "airline.rate-limit.enabled=false",
        "airline.security.revocation.enabled=false",
        // A distinct property set gives this class its own context (and its own key cache).
        "parity.case=keys-cached"
})
@AutoConfigureMockMvc
class GatewayKeysCachedParityTest {

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

    @Test
    void cachedKeysKeepWorkingDuringAnOutage() throws Exception {
        String token = ParityFixtures.token(42, "ROLE_USER");
        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isOk());

        BACKEND.jwksAvailable = false;
        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Bearer " + ParityFixtures.token(42, "ROLE_USER")))
                .andExpect(status().isOk());
        // An unknown key id cannot be resolved during the outage, but the cache is not empty: 401, not 503.
        mvc.perform(get("/api/bookings/9").header(HttpHeaders.AUTHORIZATION, "Bearer "
                        + ParityFixtures.token(ParityFixtures.Spec.valid(42, "ROLE_USER").kid("rotated-key"))))
                .andExpect(status().isUnauthorized());
    }
}
