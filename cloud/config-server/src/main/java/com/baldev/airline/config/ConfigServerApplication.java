package com.baldev.airline.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Serves configuration to every service from {@code config-repo/} (native backend) or a Git repository
 * (profile {@code git}). Clients resolve {@code ${...}} placeholders themselves, so secrets stay in each
 * service's environment and never pass through this server.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
