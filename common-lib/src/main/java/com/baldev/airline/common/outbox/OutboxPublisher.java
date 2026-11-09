package com.baldev.airline.common.outbox;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Map;

/**
 * Writes an event to the {@code outbox_event} table <em>in the caller's transaction</em>. The event is published to
 * Kafka by {@link OutboxRelay} only once that transaction has committed, and it is never lost after commit: the
 * state change and the event are one atomic write. This closes the gap of "commit, then send" publishing, where a
 * crash between the two loses the event, and of "send, then commit", where a rollback announces state that never
 * existed.
 *
 * <p>Delivery is at least once (a crash after the send but before marking the row published re-sends it), so
 * consumers must stay idempotent. Events with the same key are published in insertion order.
 *
 * <p>Table (one per service database; add it with a Flyway migration):
 * <pre>
 * create table outbox_event (
 *     id bigint not null auto_increment primary key,
 *     topic varchar(200) not null,
 *     message_key varchar(200),
 *     payload_type varchar(255) not null,
 *     payload mediumtext not null,
 *     trace_headers varchar(1000),
 *     status varchar(16) not null,            -- PENDING, PUBLISHED, FAILED (can never be published)
 *     created_at datetime(6) not null,
 *     published_at datetime(6),
 *     attempts int not null default 0,
 *     last_error varchar(1000),
 *     index idx_outbox_status_id (status, id)
 * ) engine=InnoDB;
 * </pre>
 */
public class OutboxPublisher {

    static final String INSERT = """
            insert into outbox_event (topic, message_key, payload_type, payload, trace_headers, status, created_at, attempts)
            values (?, ?, ?, ?, ?, 'PENDING', ?, 0)""";

    private final JdbcTemplate jdbcTemplate;
    private final JsonMapper jsonMapper;
    private final TraceContextCodec traceContext;
    private final Clock clock;

    public OutboxPublisher(JdbcTemplate jdbcTemplate, JsonMapper jsonMapper, TraceContextCodec traceContext, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.jsonMapper = jsonMapper;
        this.traceContext = traceContext;
        this.clock = clock;
    }

    /**
     * Records {@code event} for publication to {@code topic} with Kafka key {@code key}.
     *
     * @throws IllegalStateException if no transaction is active: without one the event would not be atomic with
     *                               the state change it announces, which is the whole point of the outbox
     */
    public void publish(String topic, String key, Object event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Outbox events must be written inside the transaction that changes state ("
                    + event.getClass().getSimpleName() + " to " + topic + ")");
        }
        Map<String, String> headers = traceContext.capture();
        jdbcTemplate.update(INSERT,
                topic,
                key,
                event.getClass().getName(),
                jsonMapper.writeValueAsString(event),
                headers.isEmpty() ? null : jsonMapper.writeValueAsString(headers),
                Timestamp.from(clock.instant()));
    }
}
