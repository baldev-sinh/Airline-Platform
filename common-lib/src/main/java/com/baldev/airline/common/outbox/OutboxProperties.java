package com.baldev.airline.common.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Transactional outbox settings, {@code airline.outbox.*}. A service opts in with {@code airline.outbox.enabled=true}
 * and a Flyway migration creating the {@code outbox_event} table (see {@link OutboxPublisher}).
 *
 * @param pollInterval  how often the relay looks for unpublished events (also the worst-case extra latency)
 * @param batchSize     events relayed per poll
 * @param sendTimeout   how long one Kafka send may take before the batch is retried on the next poll
 * @param retention     published rows older than this are deleted
 * @param cleanupInterval how often old published rows are deleted
 */
@ConfigurationProperties(prefix = "airline.outbox")
public record OutboxProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("500ms") Duration pollInterval,
        @DefaultValue("100") int batchSize,
        @DefaultValue("10s") Duration sendTimeout,
        @DefaultValue("7d") Duration retention,
        @DefaultValue("1h") Duration cleanupInterval) {
}
