package com.baldev.airline.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class NotificationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
