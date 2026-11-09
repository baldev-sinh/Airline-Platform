package com.baldev.airline.user.config;

import com.baldev.airline.common.security.TokenRevocations;
import com.baldev.airline.common.security.resource.AccessTokenDecoders;
import com.baldev.airline.common.security.resource.AccessTokenValidators;
import com.baldev.airline.common.security.resource.ServiceSecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

/**
 * user-service is an OAuth2 resource server like every other service: requests to {@code /api/**} that carry a
 * bearer token are verified by Spring Security (here with this service's own public keys, no network round trip),
 * and {@code CurrentUser} comes from that verified token. Authorization stays where it was: the gateway's access
 * rules plus the checks in the controllers. Sign-in itself ({@code /auth/**}) is AuthService, not Spring
 * Security's form login; Spring Security also provides the password hashing.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder decoder) {
        ServiceSecurityAutoConfiguration.serviceDefaults(decoder).customize(http);
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** Verifies this service's own tokens against the keys it publishes (current and still-valid previous keys). */
    @Bean
    public JwtDecoder accessTokenDecoder(SigningKeys keys, SecurityProperties properties, TokenRevocations revocations,
                                         Clock clock) {
        SecurityProperties.Jwt jwt = properties.security().jwt();
        return AccessTokenDecoders.fixed(keys.verificationKeys(),
                AccessTokenValidators.of(jwt.issuer(), jwt.audience(), revocations, clock));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Login is handled by AuthService, not Spring Security. Declaring this bean stops Boot from creating a
     * default in-memory user with a generated password that would be printed to the log.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Form/basic authentication is disabled");
        };
    }
}
