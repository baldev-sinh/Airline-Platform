package com.baldev.airline.flightops.service.support;

import com.baldev.airline.common.exception.DownstreamServiceException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.flightops.client.dto.AirlineResponse;
import com.baldev.airline.flightops.client.dto.AirportResponse;
import com.baldev.airline.flightops.client.AirlineClient;
import com.baldev.airline.flightops.client.LocationClient;
import com.baldev.airline.flightops.service.support.ReferenceData.Ref;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigurationProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReferenceDataTest {

    private final AirlineClient airlines = mock(AirlineClient.class);
    private final LocationClient locations = mock(LocationClient.class);

    private ReferenceData referenceData() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
                .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .ignoreExceptions(ResourceNotFoundException.class)
                .build());
        Resilience4JConfigurationProperties properties = new Resilience4JConfigurationProperties();
        properties.setDisableThreadPool(true);
        properties.setDisableTimeLimiter(true);
        Resilience4JCircuitBreakerFactory factory = new Resilience4JCircuitBreakerFactory(registry,
                TimeLimiterRegistry.ofDefaults(), null, properties);
        return new ReferenceData(airlines, locations, factory);
    }

    @Test
    void outageServesAPlaceholderInsteadOfFailing() {
        when(airlines.getAirlineById(7L)).thenThrow(new DownstreamServiceException("airline-core down"));

        Ref<AirlineResponse> airline = referenceData().airline(7L);

        assertThat(airline.available()).isFalse();
        assertThat(airline.value().getId()).isEqualTo(7L);
    }

    @Test
    void notFoundIsAnAnswerAndIsRethrown() {
        when(locations.getAirportById(3L)).thenThrow(ResourceNotFoundException.of("Airport", 3L));

        assertThatThrownBy(() -> referenceData().airport(3L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void openBreakerStopsCallingTheFailingService() {
        when(locations.getAirportById(1L)).thenThrow(new DownstreamServiceException("location down"));
        ReferenceData data = referenceData();

        for (int i = 0; i < 10; i++) {
            Ref<AirportResponse> airport = data.airport(1L);
            assertThat(airport.available()).isFalse();
        }

        // 4 calls fill the window and open the breaker; the remaining 6 never reach the service.
        verify(locations, times(4)).getAirportById(1L);
    }
}
