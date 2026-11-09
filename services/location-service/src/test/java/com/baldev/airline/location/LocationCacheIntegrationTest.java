package com.baldev.airline.location;

import com.baldev.airline.location.payload.request.AirportRequest;
import com.baldev.airline.location.model.Airport;
import com.baldev.airline.location.model.City;
import com.baldev.airline.location.repository.AirportRepository;
import com.baldev.airline.location.repository.CityRepository;
import com.baldev.airline.location.service.AirportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The shared Redis cache in a real service: hits skip the database, writes evict after commit, entries are
 * namespaced per service and carry a TTL. Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.cloud.config.enabled=false",
        "CONFIG_IMPORT=optional:file:../../config-repo/application.yaml,optional:file:../../config-repo/location-service.yaml",
        "eureka.client.enabled=false"
})
class LocationCacheIntegrationTest {

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

    @MockitoSpyBean
    private AirportRepository airportRepository;
    @Autowired
    private CityRepository cityRepository;
    @Autowired
    private AirportService airportService;
    @Autowired
    private StringRedisTemplate redis;

    @Test
    void readsAreCachedAndWritesEvict() {
        City city = cityRepository.save(City.builder().name("Mumbai").cityCode("BOM").countryCode("IN")
                .countryName("India").build());
        Airport airport = airportRepository.save(Airport.builder().iataCode("BOM").name("Chhatrapati Shivaji")
                .city(city).build());
        clearInvocations(airportRepository);

        assertThat(airportService.getAirportById(airport.getId()).getName()).isEqualTo("Chhatrapati Shivaji");
        assertThat(airportService.getAirportById(airport.getId()).getName()).isEqualTo("Chhatrapati Shivaji");
        verify(airportRepository, times(1)).findById(anyLong());

        String key = "airline:location-service:v1:airports::" + airport.getId();
        assertThat(redis.hasKey(key)).isTrue();
        assertThat(redis.getExpire(key)).isPositive();

        AirportRequest rename = new AirportRequest();
        rename.setName("CSMIA");
        airportService.updateAirport(airport.getId(), rename);
        assertThat(redis.hasKey(key)).isFalse();
        assertThat(airportService.getAirportById(airport.getId()).getName()).isEqualTo("CSMIA");
    }
}
