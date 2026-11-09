package com.baldev.airline.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sign-up to sign-out against real MySQL and Redis, with the real central configuration and the service verifying
 * its own tokens with Spring Security: JWKS, RS256 access tokens, refresh-token rotation, reuse detection and
 * revocation. Kafka is not needed: user.registered stays in the outbox. Skipped when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/user-service.yaml,"
                + "optional:file:../../config-repo/user-service-local.yaml",
        "eureka.client.enabled=false",
        "spring.kafka.bootstrap-servers=localhost:1",
        "airline.outbox.poll-interval=1h"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JwtDecoder verifier;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void signupRefreshReuseAndLogout() throws Exception {
        JsonNode signup = json(mvc.perform(post("/auth/signup").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Asha Rao","email":"asha@example.com","password":"long-enough-password","phone":"+919800000000"}"""))
                .andExpect(status().isCreated())
                .andReturn());
        String access = signup.get("jwt").asString();
        String refresh = signup.get("refreshToken").asString();

        // The welcome e-mail event is in the outbox, committed with the user.
        assertThat(jdbc.queryForObject("select count(*) from outbox_event where topic = 'user.registered'", Integer.class))
                .isEqualTo(1);

        // JWKS publishes the key that verifies our token; the service accepts it and ignores forged headers.
        mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA")).andExpect(jsonPath("$.keys[0].alg").value("RS256"));
        Jwt token = verifier.decode(access);
        mvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + access).header("X-User-Id", "999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(Long.parseLong(token.getSubject())));
        mvc.perform(get("/api/users/profile").header("X-User-Id", token.getSubject()))
                .andExpect(status().isUnauthorized());

        // Rotation: the refresh token is spent, a new pair is issued.
        JsonNode rotated = json(mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isOk()).andReturn());
        String newAccess = rotated.get("jwt").asString();
        assertThat(rotated.get("refreshToken").asString()).isNotEqualTo(refresh);

        // Reuse of the spent token: the whole session family and every access token are revoked.
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + rotated.get("refreshToken").asString() + "\"}"))
                .andExpect(status().isUnauthorized());
        // Even though it was issued within the same second as the revocation (iat has one-second precision).
        assertThatThrownBy(() -> verifier.decode(newAccess)).isInstanceOf(JwtValidationException.class);

        // A new sign-in in a later second works again.
        Thread.sleep(1100);

        // Sign in again, then log out: the access token stops working at once.
        JsonNode login = json(mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"asha@example.com\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isOk()).andReturn());
        String fresh = login.get("jwt").asString();
        mvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + fresh)).andExpect(status().isOk());
        mvc.perform(post("/auth/logout").header("Authorization", "Bearer " + fresh).contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + login.get("refreshToken").asString() + "\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/profile").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REVOKED"));
    }

    private static JsonNode json(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsString());
    }
}
