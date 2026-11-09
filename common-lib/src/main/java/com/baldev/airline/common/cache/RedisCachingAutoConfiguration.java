package com.baldev.airline.common.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis-backed {@code @Cacheable} for every service that has spring-data-redis on its classpath.
 *
 * <ul>
 *   <li>Caches are declared with {@link CacheSpec} beans (name + stored type); entries are plain JSON of that
 *       type, so a value read from Redis can never instantiate an arbitrary class.</li>
 *   <li>Keys: {@code airline:<service>:v<keyVersion>:<cache>::<key>}, so services never share or clobber entries.</li>
 *   <li>Every entry has a TTL ({@code airline.cache.default-ttl}, per cache {@code airline.cache.ttl.<name>});
 *       nulls are not cached; unknown JSON properties are ignored, so a rolling deploy that adds a field to a
 *       DTO still reads entries written by the previous version.</li>
 *   <li>Transaction-aware: an eviction inside a {@code @Transactional} method happens after commit, so a
 *       concurrent read cannot re-cache the old row between the eviction and the commit.</li>
 *   <li>Fails open ({@link ResilientCache}): Redis being down slows the service down; it never breaks it.</li>
 * </ul>
 *
 * <p>Declared as the primary {@link CacheManager} because Spring Cloud LoadBalancer registers its own
 * (Caffeine) cache manager for service instance lists.
 */
@AutoConfiguration(
        afterName = "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        beforeName = "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration")
@ConditionalOnClass({RedisConnectionFactory.class, CacheManager.class, MeterRegistry.class})
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnProperty(prefix = "airline.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AirlineCacheProperties.class)
@EnableCaching
public class RedisCachingAutoConfiguration {

    static final JsonMapper CACHE_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Bean
    @Primary
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, AirlineCacheProperties properties,
                                          List<CacheSpec> specs, Environment environment,
                                          ObjectProvider<MeterRegistry> meterRegistry) {
        String service = environment.getProperty("spring.application.name", "app");
        String prefix = "airline:" + service + ":v" + properties.keyVersion() + ":";

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(properties.defaultTtl())
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> prefix + cacheName + "::")
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()));

        Map<String, RedisCacheConfiguration> caches = new LinkedHashMap<>();
        for (CacheSpec spec : specs) {
            caches.put(spec.name(), defaults
                    .entryTtl(properties.ttl().getOrDefault(spec.name(), properties.defaultTtl()))
                    .serializeValuesWith(SerializationPair.fromSerializer(serializerFor(spec.type()))));
        }

        MeterRegistry registry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
        ResilientRedisCacheManager manager = new ResilientRedisCacheManager(
                RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory), defaults, caches, registry);
        manager.setTransactionAware(true);
        return manager;
    }

    @Bean
    public CachingConfigurer airlineCachingConfigurer(ObjectProvider<RedisCacheManager> cacheManager) {
        return new CachingConfigurer() {
            @Override
            public CacheManager cacheManager() {
                return cacheManager.getObject();
            }
        };
    }

    static RedisSerializer<Object> serializerFor(JavaType type) {
        return new JacksonJsonRedisSerializer<>(CACHE_MAPPER, type);
    }

    /**
     * Only declared caches exist (no runtime creation), and each one is wrapped in {@link ResilientCache} before
     * the transaction-aware decorator is applied.
     */
    static final class ResilientRedisCacheManager extends RedisCacheManager {

        private final MeterRegistry registry;

        ResilientRedisCacheManager(RedisCacheWriter writer, RedisCacheConfiguration defaults,
                                   Map<String, RedisCacheConfiguration> caches, MeterRegistry registry) {
            super(writer, defaults, false, caches);
            this.registry = registry;
        }

        @Override
        protected Cache decorateCache(Cache cache) {
            return super.decorateCache(new ResilientCache(cache, registry));
        }
    }
}
