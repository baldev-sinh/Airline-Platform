package com.baldev.airline.gateway.ratelimit;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * Token bucket in Redis, evaluated atomically by a Lua script, so every gateway instance shares one bucket per
 * client and concurrent requests cannot both take the last token. The script uses Redis's own clock, so gateway
 * clock skew does not matter. Keys expire once the bucket would be full again.
 */
public class RedisRateLimiter {

    /** Outcome of one check. {@code retryAfterSeconds} is how long until a token is available (0 if allowed). */
    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
    }

    private static final String SCRIPT = """
            local tokens_key = KEYS[1]
            local timestamp_key = KEYS[2]
            local rate = tonumber(ARGV[1])
            local capacity = tonumber(ARGV[2])
            local requested = 1
            local time = redis.call('TIME')
            local now = tonumber(time[1]) + tonumber(time[2]) / 1000000
            local ttl = math.ceil(capacity / rate * 2)
            local last_tokens = tonumber(redis.call('GET', tokens_key))
            if last_tokens == nil then last_tokens = capacity end
            local last_refreshed = tonumber(redis.call('GET', timestamp_key))
            if last_refreshed == nil then last_refreshed = 0 end
            local filled = math.min(capacity, last_tokens + math.max(0, now - last_refreshed) * rate)
            local allowed = filled >= requested
            local new_tokens = filled
            local retry_after = 0
            if allowed then
              new_tokens = filled - requested
            else
              retry_after = math.ceil((requested - filled) / rate)
            end
            redis.call('SET', tokens_key, tostring(new_tokens), 'EX', ttl)
            redis.call('SET', timestamp_key, tostring(now), 'EX', ttl)
            return { allowed and 1 or 0, math.floor(new_tokens), retry_after }
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> script;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>(SCRIPT, List.class);
    }

    public Decision tryAcquire(String policy, String clientKey, int capacity, double ratePerSecond) {
        // Hash tag {...}: both keys of a bucket land on the same slot in Redis Cluster.
        String base = "airline:ratelimit:{" + policy + ":" + clientKey + "}";
        List<?> result = redis.execute(script, List.of(base + ":tokens", base + ":ts"),
                Double.toString(ratePerSecond), Integer.toString(capacity));
        if (result == null || result.size() < 3) {
            throw new IllegalStateException("Unexpected rate limiter reply: " + result);
        }
        return new Decision(toLong(result.get(0)) == 1, toLong(result.get(1)), toLong(result.get(2)));
    }

    private static long toLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }
}
