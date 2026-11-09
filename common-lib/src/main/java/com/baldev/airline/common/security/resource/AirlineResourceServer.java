package com.baldev.airline.common.security.resource;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * The resource-server setup shared by the gateway and every service, applied to an {@link HttpSecurity} with
 * {@code http.with(...)}-style {@link Customizer} semantics:
 *
 * <ul>
 *   <li>stateless: no session, no CSRF (there are no cookies to forge), no form login, HTTP basic or logout page;</li>
 *   <li>bearer tokens from {@link AccessTokenBearerTokenResolver}, verified by the given {@link JwtDecoder};</li>
 *   <li>authorities straight from the {@code roles} claim (it already holds {@code ROLE_*} names);</li>
 *   <li>failures answered with our problem details ({@link ProblemDetailsAuthenticationEntryPoint},
 *       {@link ProblemDetailsAccessDeniedHandler}); an unreachable issuer is a 503, not Spring's default 500.</li>
 * </ul>
 */
public final class AirlineResourceServer implements Customizer<HttpSecurity> {

    private final JwtDecoder decoder;
    private final RequestMatcher tokenRequests;
    private final AuthenticationEntryPoint entryPoint;
    private final AccessDeniedHandler accessDenied;

    /**
     * @param tokenRequests requests whose bearer token is looked at; all others are treated as having none
     * @param notFound      requests to answer 404 instead of 401/403 (the gateway's hidden paths)
     */
    public AirlineResourceServer(JwtDecoder decoder, AuthErrorProfile profile, RequestMatcher tokenRequests,
                                 RequestMatcher notFound) {
        this.decoder = decoder;
        this.tokenRequests = tokenRequests;
        this.entryPoint = new ProblemDetailsAuthenticationEntryPoint(profile, notFound);
        this.accessDenied = new ProblemDetailsAccessDeniedHandler(profile, notFound);
    }

    /** Authorities are the {@code roles} claim verbatim; the principal name is {@code sub} (the user id). */
    public static JwtAuthenticationConverter rolesConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Override
    public void customize(HttpSecurity http) {
        AuthenticationEntryPointFailureHandler failures = new AuthenticationEntryPointFailureHandler(entryPoint);
        // Spring rethrows "service" failures (issuer unreachable) as a 500 by default; we answer 503 instead.
        failures.setRethrowAuthenticationServiceException(false);
        http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(new AccessTokenBearerTokenResolver(tokenRequests))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied)
                        .jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(rolesConverter()))
                        .withObjectPostProcessor(new ObjectPostProcessor<BearerTokenAuthenticationFilter>() {
                            @Override
                            public <O extends BearerTokenAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationFailureHandler(failures);
                                return filter;
                            }
                        }))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied));
    }
}
