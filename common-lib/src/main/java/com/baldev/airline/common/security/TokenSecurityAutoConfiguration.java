package com.baldev.airline.common.security;

import com.baldev.airline.common.security.JwksKeyResolver.Jwk;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Access-token verification for the gateway and every service: JWKS keys from user-service, the verifier,
 * and revocation checks in Redis.
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
}
