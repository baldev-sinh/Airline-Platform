package com.baldev.airline.common.security.resource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;


/**
 * Makes every business service an OAuth2 resource server: it verifies the caller's access token itself
 * (signature, issuer, audience, expiry, revocation) instead of trusting anything the gateway or another caller
 * claims. The gateway turns this off ({@code airline.security.service-auth.enabled=false}) and declares its own
 * chain with the access rules.
 *
 * <p>Services keep their authorization where it always was: in the code ({@code CurrentUser}, ownership checks).
 * The chain therefore permits every request and only <em>authenticates</em>:
 * <ul>
 *   <li>a request to {@code /api/**} with a bearer token must carry a valid one (401 otherwise, with the service
 *       error codes; 503 when the issuer's keys cannot be fetched);</li>
 *   <li>a request without a token is anonymous: public reads (made by flight search, for example) keep working
 *       and endpoints that need a caller answer 401 through {@code CurrentUser};</li>
 *   <li>{@code /internal/**}, {@code /actuator/**} and everything else outside {@code /api} ignore tokens
 *       (private network; Prometheus scrapes without one).</li>
 * </ul>
 */
@AutoConfiguration(after = AccessTokenDecoderAutoConfiguration.class, beforeName = {
        "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
        "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
        "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({HttpSecurity.class, JwtDecoder.class})
@ConditionalOnProperty(prefix = "airline.security.service-auth", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ServiceSecurityAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @EnableWebSecurity
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    static class ServiceFilterChain {

        @Bean
        SecurityFilterChain serviceSecurityFilterChain(HttpSecurity http, JwtDecoder decoder) throws Exception {
            serviceDefaults(decoder).customize(http);
            http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
            return http.build();
        }
    }

    /**
     * The service setup, for a service that declares its own {@link SecurityFilterChain} (user-service): verify
     * tokens on {@code /api/**} only, service error codes, and no security response headers (services never sent
     * any; the gateway adds them for browsers).
     */
    public static org.springframework.security.config.Customizer<HttpSecurity> serviceDefaults(JwtDecoder decoder) {
        AirlineResourceServer resourceServer = new AirlineResourceServer(decoder, AuthErrorProfile.SERVICE,
                PathPatternRequestMatcher.withDefaults().matcher("/api/**"), request -> false);
        return http -> {
            resourceServer.customize(http);
            http.headers(AbstractHttpConfigurer::disable);
        };
    }
}
