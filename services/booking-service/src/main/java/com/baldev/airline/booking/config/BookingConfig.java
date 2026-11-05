package com.baldev.airline.booking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class BookingConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
