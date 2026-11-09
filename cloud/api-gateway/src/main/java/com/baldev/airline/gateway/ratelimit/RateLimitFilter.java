package com.baldev.airline.gateway.ratelimit;

import com.baldev.airline.gateway.ratelimit.RateLimitProperties.Policy;
import com.baldev.airline.gateway.ratelimit.RedisRateLimiter.Decision;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Applies the first matching {@link Policy} to each request, keyed by client IP. Over the limit: 429 with
 * {@code Retry-After}. CORS preflights are never counted. If Redis is unreachable the request is let through
 * (or rejected with 503 when {@code fail-open=false}); either way it is counted in
 * {@code gateway.ratelimit.errors}.
 */
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private record CompiledPolicy(Policy policy, List<PathPattern> patterns) {
        boolean matches(String method, PathContainer path) {
            return (policy.methods().isEmpty() || policy.methods().stream().anyMatch(m -> m.equalsIgnoreCase(method)))
                    && patterns.stream().anyMatch(p -> p.matches(path));
        }
    }

    private final List<CompiledPolicy> policies;
    private final RedisRateLimiter limiter;
    private final ClientIpResolver clientIp;
    private final boolean failOpen;
    private final MeterRegistry meterRegistry;

    public RateLimitFilter(RateLimitProperties properties, RedisRateLimiter limiter, ClientIpResolver clientIp,
                           MeterRegistry meterRegistry) {
        PathPatternParser parser = PathPatternParser.defaultInstance;
        this.policies = properties.policies().stream()
                .map(p -> new CompiledPolicy(p, p.paths().stream().map(parser::parse).toList()))
                .toList();
        this.limiter = limiter;
        this.clientIp = clientIp;
        this.failOpen = properties.failOpen();
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        CompiledPolicy match = policies.stream().filter(p -> p.matches(request.getMethod(), path)).findFirst().orElse(null);
        if (match == null || match.policy().exempt()) {
            chain.doFilter(request, response);
            return;
        }
        Policy policy = match.policy();
        Decision decision;
        try {
            decision = limiter.tryAcquire(policy.name(), clientIp.resolve(request), policy.capacity(), policy.ratePerSecond());
        } catch (RuntimeException e) {
            meterRegistry.counter("gateway.ratelimit.errors", "policy", policy.name()).increment();
            log.warn("Rate limiter unavailable for policy {} ({}); {}", policy.name(), e.toString(),
                    failOpen ? "allowing request" : "rejecting request");
            if (failOpen) {
                chain.doFilter(request, response);
            } else {
                write(response, 503, "RATE_LIMITER_UNAVAILABLE", "Please try again shortly", 5);
            }
            return;
        }
        response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(decision.remaining(), 0)));
        if (!decision.allowed()) {
            meterRegistry.counter("gateway.ratelimit.rejected", "policy", policy.name()).increment();
            write(response, 429, "RATE_LIMITED", "Too many requests, please slow down",
                    Math.max(decision.retryAfterSeconds(), 1));
            return;
        }
        chain.doFilter(request, response);
    }

    private static void write(HttpServletResponse response, int status, String code, String detail, long retryAfter)
            throws IOException {
        response.setStatus(status);
        response.setHeader("Retry-After", Long.toString(retryAfter));
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String title = status == 429 ? "Too Many Requests" : "Service Unavailable";
        response.getWriter().write(String.format(Locale.ROOT,
                "{\"type\":\"about:blank\",\"title\":\"%s\",\"status\":%d,\"detail\":\"%s\",\"code\":\"%s\"}",
                title, status, detail, code));
    }
}
