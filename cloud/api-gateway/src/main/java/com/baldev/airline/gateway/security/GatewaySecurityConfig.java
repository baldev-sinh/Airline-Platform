package com.baldev.airline.gateway.security;

import com.baldev.airline.common.security.resource.AirlineResourceServer;
import com.baldev.airline.common.security.resource.AuthErrorProfile;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Spring Security at the edge. Two filter chains:
 *
 * <ol>
 *   <li><b>Management port</b> (actuator, 5100): open, because that port is never published; Prometheus scrapes
 *       it without a token. On the public port actuator paths are hidden like any other DENY rule.</li>
 *   <li><b>Public port</b> (5000): an OAuth2 resource server.
 *     <ul>
 *       <li>{@link GatewayAccessRules} decides per request: public, signed in, airline owner, admin, or hidden;</li>
 *       <li>tokens are verified with user-service's published keys (signature, issuer, audience, expiry,
 *           revocation) by the shared {@link AirlineResourceServer} setup; public endpoints ignore them;</li>
 *       <li>failures are problem details: 401 (no or bad token), 403 (wrong role), 404 (hidden or unknown),
 *           503 (issuer's keys unavailable);</li>
 *       <li>CORS is answered here, before authentication, so preflights need no token and the browser can read
 *           a 401 (and refresh its token).</li>
 *     </ul>
 *   </li>
 * </ol>
 *
 * Security response headers come from {@code EdgeWebConfig}'s filter, which runs before the rate limiter so that
 * even a 429 carries them; Spring Security's own header writer is turned off to avoid sending them twice.
 */
@Configuration
@EnableWebSecurity
public class GatewaySecurityConfig {

    /** Port of the separate management server once it has started; -1 until then (or when there is none). */
    private final AtomicInteger managementPort = new AtomicInteger(-1);

    @Bean
    ApplicationListener<WebServerInitializedEvent> managementPortListener() {
        return event -> {
            if ("management".equals(event.getApplicationContext().getServerNamespace())) {
                managementPort.set(event.getWebServer().getPort());
            }
        };
    }

    @Bean
    @Order(1)
    SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) {
        RequestMatcher onManagementPort = request -> managementPort.get() > 0 && request.getLocalPort() == managementPort.get();
        http.securityMatcher(onManagementPort)
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .headers(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain gatewaySecurityFilterChain(HttpSecurity http, JwtDecoder decoder) {
        RequestMatcher tokenRequests = request -> !GatewayAccessRules.publicRequests().matches(request);
        new AirlineResourceServer(decoder, AuthErrorProfile.GATEWAY, tokenRequests, GatewayAccessRules.hiddenRequests())
                .customize(http);
        http.cors(Customizer.withDefaults())
                .headers(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                    // An error dispatch renders a response that the original request was already allowed to get.
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
                    GatewayAccessRules.apply(auth);
                });
        return http.build();
    }

    /** Explicit origin allow-list from configuration; no wildcard, and no credentials, so nothing leaks cross-origin. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${airline.cors.allowed-origins:}") String allowedOrigins) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        String[] origins = StringUtils.commaDelimitedListToStringArray(allowedOrigins.replace(" ", ""));
        if (origins.length == 0) {
            return source;
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.asList(origins));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "traceparent"));
        cors.setExposedHeaders(List.of("Location", "Retry-After", "X-RateLimit-Remaining"));
        cors.setAllowCredentials(false);
        cors.setMaxAge(3600L);
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
