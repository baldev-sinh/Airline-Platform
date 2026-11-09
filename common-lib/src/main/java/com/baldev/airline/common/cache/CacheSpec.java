package com.baldev.airline.common.cache;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.type.TypeFactory;

import java.util.List;

/**
 * Declares a cache and the exact type it stores. Register one bean per cache in the owning service:
 *
 * <pre>{@code
 * @Bean CacheSpec airportsCache() { return CacheSpec.of("airports", AirportResponse.class); }
 * @Bean CacheSpec allAirportsCache() { return CacheSpec.listOf("airports-all", AirportResponse.class); }
 * }</pre>
 *
 * <p>Entries are stored as plain JSON of that type, with no class names inside. Reading an entry can therefore
 * only ever produce the declared type, which rules out deserialization gadget attacks through Redis, and works
 * for immutable collections such as {@code Stream.toList()} results. Using a cache name that has no spec fails.
 */
public record CacheSpec(String name, JavaType type) {

    public static CacheSpec of(String name, Class<?> type) {
        return new CacheSpec(name, TypeFactory.createDefaultInstance().constructType(type));
    }

    public static CacheSpec listOf(String name, Class<?> elementType) {
        TypeFactory types = TypeFactory.createDefaultInstance();
        return new CacheSpec(name, types.constructCollectionType(List.class, elementType));
    }
}
