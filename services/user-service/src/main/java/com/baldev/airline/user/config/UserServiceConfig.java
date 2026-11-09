package com.baldev.airline.user.config;

import com.baldev.airline.common.security.JwksKeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.Duration;

@Configuration
@EnableScheduling // RefreshTokenService cleanup
public class UserServiceConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** user-service verifies its own tokens with its own keys instead of fetching its JWKS over the network. */
    @Bean
    public JwksKeyResolver jwksKeyResolver(SigningKeys keys, Clock clock) {
        return new JwksKeyResolver(keys::publishedKeys, clock, Duration.ofDays(3650), Duration.ZERO);
    }
}
