package com.baldev.airline.common.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

/**
 * Transactional outbox for services that set {@code airline.outbox.enabled=true}: an {@link OutboxPublisher} to
 * write events in the business transaction and an {@link OutboxRelay} that publishes them to Kafka.
 */
@AutoConfiguration(afterName = {
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration"})
@ConditionalOnClass({KafkaTemplate.class, JdbcTemplate.class, MeterRegistry.class})
@ConditionalOnProperty(prefix = "airline.outbox", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    /** Same mapper for writing and relaying, so the payload always reads back into the class it came from. */
    private static final JsonMapper OUTBOX_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Bean
    @ConditionalOnMissingBean
    public OutboxPublisher outboxPublisher(JdbcTemplate jdbcTemplate, ObjectProvider<TraceContextCodec> traceContext,
                                           ObjectProvider<Clock> clock) {
        return new OutboxPublisher(jdbcTemplate, OUTBOX_MAPPER, traceContext.getIfAvailable(() -> TraceContextCodec.NONE),
                clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxRelay outboxRelay(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
                                   KafkaTemplate<String, Object> kafkaTemplate, ObjectProvider<TraceContextCodec> traceContext,
                                   OutboxProperties properties, ObjectProvider<Clock> clock,
                                   ObjectProvider<MeterRegistry> meterRegistry) {
        return new OutboxRelay(jdbcTemplate, transactionManager, kafkaTemplate, OUTBOX_MAPPER,
                traceContext.getIfAvailable(() -> TraceContextCodec.NONE), properties,
                clock.getIfAvailable(Clock::systemUTC), meterRegistry.getIfAvailable(SimpleMeterRegistry::new));
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Tracer.class)
    static class TracingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        TraceContextCodec outboxTraceContextCodec(ObjectProvider<Tracer> tracer, ObjectProvider<Propagator> propagator) {
            Tracer t = tracer.getIfAvailable();
            Propagator p = propagator.getIfAvailable();
            return t != null && p != null ? new MicrometerTraceContextCodec(t, p) : TraceContextCodec.NONE;
        }
    }
}
