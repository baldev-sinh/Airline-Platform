package com.baldev.airline.common.security.parity;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Issuer unreachable and no key cached: a service answers 503 (retry), never 401 (which would sign users out). */
@SpringBootTest(classes = ServiceParityApp.class, properties = {
        "spring.application.name=parity-service",
        "parity.case=keys-unavailable"
})
@AutoConfigureMockMvc
class ServiceKeysUnavailableParityTest {

    static final ServiceParityApp.Jwks JWKS = new ServiceParityApp.Jwks();

    @DynamicPropertySource
    static void jwks(DynamicPropertyRegistry registry) {
        JWKS.available = false;
        registry.add("airline.security.jwt.jwks-uri", JWKS::uri);
    }

    @AfterAll
    static void stop() {
        JWKS.close();
    }

    @Autowired
    MockMvc mvc;

    @Test
    void answers503() throws Exception {
        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + ServiceParityApp.token(42, "ROLE_USER")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(content().json("""
                        {"type":"about:blank","title":"Service Unavailable","status":503,"detail":"Authentication is temporarily unavailable, please retry","code":"AUTH_UNAVAILABLE"}""",
                        JsonCompareMode.STRICT));
    }

    @Test
    void anonymousReadsAreUnaffected() throws Exception {
        mvc.perform(get("/api/public")).andExpect(status().isOk());
        mvc.perform(get("/internal/thing").header(HttpHeaders.AUTHORIZATION, "Bearer whatever")).andExpect(status().isOk());
    }
}
