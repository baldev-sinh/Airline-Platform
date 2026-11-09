package com.baldev.airline.gateway.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Actuator lives only on the private management port: Prometheus can scrape it there without a token, while the
 * public port answers 404 for every actuator path.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/api-gateway.yaml",
        "eureka.client.enabled=false",
        "management.server.port=0",
        "airline.rate-limit.enabled=false",
        "airline.security.revocation.enabled=false"
})
class GatewayManagementPortParityTest {

    static final ParityFixtures.Backend BACKEND = new ParityFixtures.Backend();

    @DynamicPropertySource
    static void backend(DynamicPropertyRegistry registry) {
        ParityFixtures.routeAllServicesTo(registry, BACKEND.baseUrl());
    }

    @AfterAll
    static void stop() {
        BACKEND.close();
    }

    @LocalServerPort
    int publicPort;

    @LocalManagementPort
    int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void prometheusIsOpenOnTheManagementPortOnly() throws Exception {
        assertThat(status(managementPort, "/actuator/prometheus")).isEqualTo(200);
        assertThat(status(managementPort, "/actuator/info")).isEqualTo(200);
        assertThat(status(publicPort, "/actuator/prometheus")).isEqualTo(404);
        assertThat(status(publicPort, "/actuator/info")).isEqualTo(404);
    }

    @Test
    void routedTrafficStaysOnThePublicPort() throws Exception {
        assertThat(status(publicPort, "/api/flights/search")).isEqualTo(200);
        assertThat(status(publicPort, "/api/bookings/9")).isEqualTo(401);
    }

    private int status(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
