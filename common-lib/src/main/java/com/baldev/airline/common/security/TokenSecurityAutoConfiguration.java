package com.baldev.airline.common.security;

import com.baldev.airline.common.security.JwksKeyResolver.Jwk;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Access-token verification for the gateway and every service: JWKS keys from user-service, the verifier,
 * revocation checks in Redis, and (in services) the {@link JwtAuthenticationFilter}.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "org.springframework.cloud.client.loadbalancer.LoadBalancerAutoConfiguration"})
@ConditionalOnClass({Jwts.class, RestClient.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TokenSecurityProperties.class)
public class TokenSecurityAutoConfiguration {

    record JwksDocument(List<Jwk> keys) {
    }

    @Bean
    @ConditionalOnMissingBean
    public JwksKeyResolver jwksKeyResolver(TokenSecurityProperties properties,
                                           ObjectProvider<LoadBalancerClient> loadBalancer,
                                           ObjectProvider<Clock> clock) {
        RestClient.Builder builder = RestClient.builder();
        // http://user-service/... is a Eureka service id; plain URLs work too when no load balancer is present.
        loadBalancer.ifAvailable(lb -> builder.requestInterceptor(new LoadBalancerInterceptor(lb)));
        RestClient client = builder.build();
        String uri = properties.jwt().jwksUri();
        return new JwksKeyResolver(() -> {
            JwksDocument document = client.get().uri(uri).retrieve().body(JwksDocument.class);
            return document != null && document.keys() != null ? document.keys() : List.of();
        }, clock.getIfAvailable(Clock::systemUTC), properties.jwt().jwksMaxAge(), Duration.ofSeconds(30));
    }

    @Bean
    @ConditionalOnMissingBean
    public TokenRevocations tokenRevocations(TokenSecurityProperties properties, ObjectProvider<Clock> clock,
                                             ObjectProvider<StringRedisTemplate> redis) {
        StringRedisTemplate template = properties.revocation().enabled() ? redis.getIfAvailable() : null;
        return template != null
                ? new RedisTokenRevocations(template, clock.getIfAvailable(Clock::systemUTC), properties.revocation().failOpen())
                : TokenRevocations.NONE;
    }

    @Bean
    @ConditionalOnMissingBean
    public AccessTokenVerifier accessTokenVerifier(JwksKeyResolver keys, TokenRevocations revocations,
                                                   TokenSecurityProperties properties, ObjectProvider<Clock> clock) {
        return new AccessTokenVerifier(keys, revocations, properties.jwt().issuer(), properties.jwt().audience(),
                clock.getIfAvailable(Clock::systemUTC));
    }

    /** Services verify tokens themselves; the gateway disables this ({@code service-auth.enabled=false}). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "airline.security.service-auth", name = "mode", havingValue = "jwt", matchIfMissing = true)
    static class ServiceAuthentication {

        @Bean
        @ConditionalOnProperty(prefix = "airline.security.service-auth", name = "enabled", havingValue = "true", matchIfMissing = true)
        FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilter(AccessTokenVerifier verifier) {
            FilterRegistrationBean<JwtAuthenticationFilter> registration =
                    new FilterRegistrationBean<>(new JwtAuthenticationFilter(verifier));
            registration.addUrlPatterns("/api/*");
            // Early, but after the observation/tracing filter so rejected requests are still traced.
            registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
            registration.setName("jwtAuthenticationFilter");
            return registration;
        }
    }
}
