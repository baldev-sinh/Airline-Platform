package com.baldev.airline.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class UserServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
