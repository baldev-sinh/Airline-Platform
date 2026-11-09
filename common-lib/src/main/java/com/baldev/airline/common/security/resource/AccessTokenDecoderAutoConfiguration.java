package com.baldev.airline.common.security.resource;

import com.baldev.airline.common.security.TokenRevocations;
import com.baldev.airline.common.security.TokenSecurityAutoConfiguration;
import com.baldev.airline.common.security.TokenSecurityProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.LoadBalancerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.function.UnaryOperator;

/**
 * The {@link JwtDecoder} that the gateway and every service use to verify access tokens: the issuer's published
 * keys (user-service's JWKS, through service discovery) plus {@link AccessTokenValidators}. A service that
 * verifies tokens differently (user-service uses its own keys) declares its own decoder bean.
 */
@AutoConfiguration(after = TokenSecurityAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(JwtDecoder.class)
public class AccessTokenDecoderAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder accessTokenDecoder(TokenSecurityProperties properties, TokenRevocations revocations,
                                        ObjectProvider<Clock> clock, ObjectProvider<LoadBalancerClient> loadBalancer) {
        TokenSecurityProperties.Jwt jwt = properties.jwt();
        requireText(jwt.issuer(), "airline.security.jwt.issuer");
        requireText(jwt.audience(), "airline.security.jwt.audience");
        requireText(jwt.jwksUri(), "airline.security.jwt.jwks-uri");
        return AccessTokenDecoders.remote(jwt.jwksUri(), jwt.jwksMaxAge(), jwksClient(loadBalancer.getIfAvailable()),
                AccessTokenValidators.of(jwt.issuer(), jwt.audience(), revocations, clock.getIfAvailable(Clock::systemUTC)));
    }

    /** {@code http://user-service/...} is a service id; a plain URL works too when no load balancer is present. */
    static RestClient jwksClient(LoadBalancerClient loadBalancer) {
        UnaryOperator<RestClient.Builder> customizer = loadBalancer == null
                ? UnaryOperator.identity()
                : builder -> builder.requestInterceptor(new LoadBalancerInterceptor(loadBalancer));
        return AccessTokenDecoders.jwksClient(customizer);
    }

    /** Fail fast at startup instead of rejecting every token at run time. */
    private static void requireText(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " must be set");
        }
    }
}
