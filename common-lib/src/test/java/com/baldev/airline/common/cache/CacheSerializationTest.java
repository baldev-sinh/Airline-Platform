package com.baldev.airline.common.cache;

import com.baldev.airline.common.enums.CabinClassType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CacheSerializationTest {

    /** Shaped like the services' response DTOs (Lombok @Data: no-arg constructor, getters and setters). */
    public static class Fare {
        private Long id;
        private CabinClassType cabinClass;
        private BigDecimal totalPrice;
        private Instant createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public CabinClassType getCabinClass() {
            return cabinClass;
        }

        public void setCabinClass(CabinClassType cabinClass) {
            this.cabinClass = cabinClass;
        }

        public BigDecimal getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }

    @Test
    void roundTripsImmutableListsOfDtos() {
        Fare fare = new Fare();
        fare.setId(7L);
        fare.setCabinClass(CabinClassType.ECONOMY);
        fare.setTotalPrice(new BigDecimal("4999.00"));
        fare.setCreatedAt(Instant.parse("2026-01-02T03:04:05.123456Z"));
        RedisSerializer<Object> serializer =
                RedisCachingAutoConfiguration.serializerFor(CacheSpec.listOf("fares", Fare.class).type());

        // Stream.toList() is a final JDK class: polymorphic typing would not record it, typed caches do not care.
        Object back = serializer.deserialize(serializer.serialize(Stream.of(fare).toList()));

        assertThat(back).isInstanceOf(List.class);
        Fare copy = (Fare) ((List<?>) back).getFirst();
        assertThat(copy.getTotalPrice()).isEqualByComparingTo("4999.00");
        assertThat(copy.getCabinClass()).isEqualTo(CabinClassType.ECONOMY);
        assertThat(copy.getCreatedAt()).isEqualTo(fare.getCreatedAt());
    }

    @Test
    void readsOnlyTheDeclaredTypeAndToleratesNewFields() {
        RedisSerializer<Object> serializer =
                RedisCachingAutoConfiguration.serializerFor(CacheSpec.of("fares", Fare.class).type());
        // Class names inside the payload are just data; a field added by a newer version is ignored.
        byte[] json = "{\"@class\":\"java.net.URI\",\"id\":3,\"addedInV2\":true}".getBytes(StandardCharsets.UTF_8);

        assertThat(serializer.deserialize(json)).isInstanceOf(Fare.class).extracting("id").isEqualTo(3L);
    }
}
