package com.baldev.airline.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Boots the real gateway with its central configuration (config-repo) and a real Redis: internal paths are hidden,
 * protected routes need a token, and the login rate limit answers 429 before anything is proxied. No backend
 * service runs, so allowed requests end in 503 from the load balancer. Skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/api-gateway.yaml",
        "eureka.client.enabled=false",
        "management.server.port=-1"
})
@AutoConfigureMockMvc
class GatewayIntegrationTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void edgeRulesApplyBeforeAnythingIsProxied() throws Exception {
        mvc.perform(get("/internal/users/1")).andExpect(status().isNotFound());
        mvc.perform(get("/api/bookings/1")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/bookings/1").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());

        // Login policy: 10 attempts per client, then 429 with Retry-After (config-repo/api-gateway.yaml).
        int limited = 0;
        for (int i = 0; i < 12; i++) {
            int code = mvc.perform(post("/auth/login").with(r -> {
                        r.setRemoteAddr("203.0.113.77");
                        return r;
                    }).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andReturn().getResponse().getStatus();
            if (code == 429) {
                limited++;
            } else {
                assertThat(code).as("allowed logins reach the (absent) user-service").isNotEqualTo(401);
            }
        }
        assertThat(limited).isEqualTo(2);
        mvc.perform(post("/auth/login").with(r -> {
                    r.setRemoteAddr("203.0.113.77");
                    return r;
                }).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }
}
