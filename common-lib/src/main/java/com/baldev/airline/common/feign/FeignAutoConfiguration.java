package com.baldev.airline.common.feign;

import feign.RequestInterceptor;
import feign.codec.ErrorDecoder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Shared Feign defaults for every service that uses OpenFeign. Timeouts are configured per service in YAML. */
@AutoConfiguration
@ConditionalOnClass(name = "feign.Feign")
public class FeignAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ErrorDecoder.class)
    public ErrorDecoder feignErrorDecoder() {
        return new FeignErrorDecoder();
    }

    /**
     * Token relay: a call made while handling a user's request carries that user's bearer token, so the called
     * service can verify the identity itself (it no longer trusts identity headers). Calls made outside a request
     * (Kafka listeners, scheduled jobs) carry none and must only use endpoints that need no user.
     */
    @Bean
    @ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
    public RequestInterceptor bearerTokenRelay() {
        return template -> {
            if (template.headers().containsKey(HttpHeaders.AUTHORIZATION)) {
                return;
            }
            RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
            if (attributes instanceof ServletRequestAttributes servlet) {
                String authorization = servlet.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
                if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    template.header(HttpHeaders.AUTHORIZATION, authorization);
                }
            }
        };
    }
}
