package com.baldev.airline.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling // RefreshTokenService cleanup
public class UserServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

}
