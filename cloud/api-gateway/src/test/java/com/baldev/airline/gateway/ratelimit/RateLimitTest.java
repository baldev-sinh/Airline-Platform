package com.baldev.airline.gateway.ratelimit;

import com.baldev.airline.gateway.ratelimit.RateLimitProperties.Policy;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The token bucket against a real Redis, and the filter around it. Skipped without Docker. */
@Testcontainers(disabledWithoutDocker = true)
class RateLimitTest {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8.2-alpine").withExposedPorts(6379);

    private static StringRedisTemplate redis;

    @BeforeAll
    static void connect() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        factory.start();
        redis = new StringRedisTemplate(factory);
    }

    @Test
    void bucketAllowsTheBurstThenRejectsWithRetryAfter() {
        RedisRateLimiter limiter = new RedisRateLimiter(redis);

        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("t1", "198.51.100.1", 3, 1.0 / 60).allowed()).isTrue();
        }
        RedisRateLimiter.Decision rejected = limiter.tryAcquire("t1", "198.51.100.1", 3, 1.0 / 60);

        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
        // Buckets are per client.
        assertThat(limiter.tryAcquire("t1", "198.51.100.2", 3, 1.0 / 60).allowed()).isTrue();
        assertThat(redis.getExpire("airline:ratelimit:{t1:198.51.100.1}:tokens")).isPositive();
    }

    @Test
    void filterAnswers429WithProblemDetailsAndSkipsExemptAndUnmatchedPaths() throws Exception {
        RateLimitFilter filter = filter(new RedisRateLimiter(redis), true);

        assertThat(post(filter, "/auth/login").getStatus()).isEqualTo(200);
        MockHttpServletResponse limited = post(filter, "/auth/login");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotBlank();
        assertThat(limited.getContentAsString()).contains("RATE_LIMITED");

        for (int i = 0; i < 5; i++) {
            assertThat(post(filter, "/api/payments/webhooks/razorpay").getStatus()).isEqualTo(200);
            assertThat(post(filter, "/auth/signup").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void redisOutageFailsOpenOrClosedAsConfigured() throws Exception {
        RedisRateLimiter broken = mock(RedisRateLimiter.class);
        when(broken.tryAcquire(anyString(), anyString(), anyInt(), anyDouble()))
                .thenThrow(new RedisConnectionFailureException("down"));

        assertThat(post(filter(broken, true), "/auth/login").getStatus()).isEqualTo(200);
        assertThat(post(filter(broken, false), "/auth/login").getStatus()).isEqualTo(503);
    }

    private static RateLimitFilter filter(RedisRateLimiter limiter, boolean failOpen) {
        RateLimitProperties properties = new RateLimitProperties(true, failOpen, List.of("127.0.0.0/8"), List.of(
                new Policy("webhooks", List.of("POST"), List.of("/api/payments/webhooks/**"), 0, 0, null, true),
                new Policy("login-" + System.nanoTime(), List.of("POST"), List.of("/auth/login"), 1, 1, Duration.ofHours(1), false)));
        return new RateLimitFilter(properties, limiter, new ClientIpResolver(properties.trustedProxies()),
                new SimpleMeterRegistry());
    }

    private static MockHttpServletResponse post(RateLimitFilter filter, String path) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr("203.0.113.50");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
