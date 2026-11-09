package com.baldev.airline.common.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

/**
 * Access-token settings ({@code airline.security.*}) and revocation checks in Redis, shared by the gateway and
 * every service. Verification itself is Spring Security's resource server; see
 * {@code common.security.resource.AccessTokenDecoderAutoConfiguration}.
 */
@AutoConfiguration(afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(TokenSecurityProperties.class)
public class TokenSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TokenRevocations tokenRevocations(TokenSecurityProperties properties, ObjectProvider<Clock> clock,
                                             ObjectProvider<StringRedisTemplate> redis) {
        StringRedisTemplate template = properties.revocation().enabled() ? redis.getIfAvailable() : null;
        return template != null
                ? new RedisTokenRevocations(template, clock.getIfAvailable(Clock::systemUTC), properties.revocation().failOpen())
                : TokenRevocations.NONE;
    }
}
