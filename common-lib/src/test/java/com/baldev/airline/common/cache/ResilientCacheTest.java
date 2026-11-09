package com.baldev.airline.common.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResilientCacheTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void redisOutageIsAMissNotAnError() {
        Cache broken = mock(Cache.class);
        when(broken.getName()).thenReturn("airlines");
        when(broken.get(any())).thenThrow(new RedisConnectionFailureException("down"));
        doThrow(new RedisConnectionFailureException("down")).when(broken).put(any(), any());
        doThrow(new RedisConnectionFailureException("down")).when(broken).evict(any());

        ResilientCache cache = new ResilientCache(broken, registry);

        assertThat(cache.get(1L)).isNull();
        cache.put(1L, "value");
        cache.evict(1L);
        AtomicInteger loads = new AtomicInteger();
        assertThat(cache.get(1L, () -> "loaded-" + loads.incrementAndGet())).isEqualTo("loaded-1");
        assertThat(registry.counter("cache.errors", "cache", "airlines", "operation", "get").count()).isEqualTo(2.0);
        assertThat(registry.counter("cache.gets", "cache", "airlines", "result", "miss").count()).isEqualTo(2.0);
    }

    @Test
    void countsHitsAndMissesAndSkipsNulls() {
        ResilientCache cache = new ResilientCache(new ConcurrentMapCache("fares"), registry);

        assertThat(cache.get("k")).isNull();
        cache.put("k", null);
        assertThat(cache.get("k")).isNull();
        cache.put("k", 42);
        assertThat(cache.get("k", Integer.class)).isEqualTo(42);

        assertThat(registry.counter("cache.gets", "cache", "fares", "result", "hit").count()).isEqualTo(1.0);
        assertThat(registry.counter("cache.gets", "cache", "fares", "result", "miss").count()).isEqualTo(2.0);
    }

    @Test
    void loaderExceptionsPropagate() {
        ResilientCache cache = new ResilientCache(new ConcurrentMapCache("flights"), registry);

        assertThatThrownBy(() -> cache.get("k", () -> {
            throw new IllegalStateException("not found");
        })).isInstanceOf(Cache.ValueRetrievalException.class).hasRootCauseMessage("not found");
        assertThat(cache.get("k")).isNull();
    }
}
