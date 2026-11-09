package com.baldev.airline.common.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;

import java.util.concurrent.Callable;

/**
 * Makes the cache an optimization, never a dependency: when Redis is down or an entry cannot be read, the lookup
 * is a miss (the method runs against the database) instead of a 500. Writes and evictions that fail are logged;
 * a failed eviction leaves a stale entry for at most its TTL, which is why every cache has one.
 *
 * <p>Also records {@code cache.gets{result=hit|miss}} and {@code cache.errors{operation}} per cache.
 */
final class ResilientCache implements Cache {

    private static final Logger log = LoggerFactory.getLogger(ResilientCache.class);

    private final Cache delegate;
    private final Counter hits;
    private final Counter misses;
    private final MeterRegistry registry;

    ResilientCache(Cache delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
        this.hits = Counter.builder("cache.gets").tag("cache", delegate.getName()).tag("result", "hit").register(registry);
        this.misses = Counter.builder("cache.gets").tag("cache", delegate.getName()).tag("result", "miss").register(registry);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public @Nullable ValueWrapper get(Object key) {
        try {
            ValueWrapper value = delegate.get(key);
            (value != null ? hits : misses).increment();
            return value;
        } catch (RuntimeException e) {
            failed("get", key, e);
            misses.increment();
            // An unreadable entry (e.g. written by an older DTO version) is dropped so it is rebuilt next time.
            evict(key);
            return null;
        }
    }

    @Override
    public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
        ValueWrapper wrapper = get(key);
        Object value = wrapper != null ? wrapper.get() : null;
        if (value != null && type != null && !type.isInstance(value)) {
            evict(key);
            return null;
        }
        @SuppressWarnings("unchecked")
        T typed = (T) value;
        return typed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> @Nullable T get(Object key, Callable<T> valueLoader) {
        ValueWrapper cached = get(key);
        if (cached != null) {
            return (T) cached.get();
        }
        T value;
        try {
            value = valueLoader.call();
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
        put(key, value);
        return value;
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        if (value == null) {
            return; // null values are not cached
        }
        try {
            delegate.put(key, value);
        } catch (RuntimeException e) {
            failed("put", key, e);
        }
    }

    @Override
    public @Nullable ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        if (value == null) {
            return null;
        }
        try {
            return delegate.putIfAbsent(key, value);
        } catch (RuntimeException e) {
            failed("putIfAbsent", key, e);
            return new SimpleValueWrapper(null);
        }
    }

    @Override
    public void evict(Object key) {
        try {
            delegate.evict(key);
        } catch (RuntimeException e) {
            log.error("Cache evict failed: cache={} key={}; entry may stay stale until its TTL", getName(), key, e);
            count("evict");
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        evict(key);
        return false;
    }

    @Override
    public void clear() {
        try {
            delegate.clear();
        } catch (RuntimeException e) {
            log.error("Cache clear failed: cache={}; entries may stay stale until their TTL", getName(), e);
            count("clear");
        }
    }

    @Override
    public boolean invalidate() {
        clear();
        return false;
    }

    private void failed(String operation, Object key, RuntimeException e) {
        log.warn("Cache {} failed: cache={} key={} ({}); continuing without cache", operation, getName(), key, e.toString());
        count(operation);
    }

    private void count(String operation) {
        registry.counter("cache.errors", "cache", getName(), "operation", operation).increment();
    }
}
