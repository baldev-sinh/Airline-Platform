package com.baldev.airline.seat.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(SeatProperties.class)
public class SeatServiceConfig {

    /** Injected so time-dependent logic (hold expiry) is testable. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
