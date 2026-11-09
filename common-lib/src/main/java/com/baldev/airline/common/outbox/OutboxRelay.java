package com.baldev.airline.common.outbox;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Moves committed {@code outbox_event} rows to Kafka, oldest first.
 *
 * <p><b>Ordering.</b> Rows are claimed with a plain {@code SELECT ... FOR UPDATE} (not {@code SKIP LOCKED}): when
 * several instances of a service run, a second relay waits for the first one's batch instead of publishing the
 * next rows in parallel, so events for the same key are never reordered. The claim runs at READ COMMITTED, which
 * locks only the claimed rows (no gap locks), so business transactions inserting new events never wait on it.
 *
 * <p><b>Failures.</b> A Kafka failure stops the batch (later events must not overtake the failed one) and the
 * same row is retried on the next poll, forever: transient broker problems never drop events. A row that can
 * never be published (unreadable payload, oversized record, bad topic) is marked {@code FAILED}, logged as
 * ACTION REQUIRED and counted in {@code outbox.events{state=failed}}, so it cannot block the rows behind it.
 */
public class OutboxRelay implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    static final String CLAIM = """
            select id, topic, message_key, payload_type, payload, trace_headers from outbox_event
            where status = 'PENDING' order by id limit ? for update""";
    static final String MARK_PUBLISHED = """
            update outbox_event set status = 'PUBLISHED', published_at = ?, attempts = attempts + 1, last_error = null
            where id = ?""";
    static final String MARK_RETRY = "update outbox_event set attempts = attempts + 1, last_error = ? where id = ?";
    static final String MARK_FAILED = "update outbox_event set status = 'FAILED', attempts = attempts + 1, last_error = ? where id = ?";
    static final String CLEANUP = "delete from outbox_event where status = 'PUBLISHED' and published_at < ? limit 1000";
    static final String COUNT = "select status, count(*) from outbox_event where status in ('PENDING', 'FAILED') group by status";

    /** Drain at most this many batches per poll, so a backlog clears quickly without starving the scheduler. */
    private static final int MAX_BATCHES_PER_POLL = 20;
    private static final String ALLOWED_PAYLOAD_PACKAGE = "com.baldev.airline.";

    record OutboxRow(long id, String topic, String key, String payloadType, String payload, String traceHeaders) {
    }

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate claimTransaction;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JsonMapper jsonMapper;
    private final TraceContextCodec traceContext;
    private final OutboxProperties properties;
    private final Clock clock;
    private final Counter published;
    private final Counter retries;
    private final AtomicLong pending = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();

    private volatile ScheduledExecutorService executor;

    public OutboxRelay(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager,
                       KafkaTemplate<String, Object> kafkaTemplate, JsonMapper jsonMapper,
                       TraceContextCodec traceContext, OutboxProperties properties, Clock clock,
                       MeterRegistry meterRegistry) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
        this.traceContext = traceContext;
        this.properties = properties;
        this.clock = clock;
        this.claimTransaction = new TransactionTemplate(transactionManager);
        this.claimTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.claimTransaction.setName("outbox-relay");
        this.published = Counter.builder("outbox.published").description("Events relayed to Kafka").register(meterRegistry);
        this.retries = Counter.builder("outbox.send.failures").description("Kafka sends that will be retried").register(meterRegistry);
        Gauge.builder("outbox.events", pending, AtomicLong::get).tag("state", "pending")
                .description("Committed events not yet on Kafka").register(meterRegistry);
        Gauge.builder("outbox.events", failed, AtomicLong::get).tag("state", "failed")
                .description("Events that can never be published; need a human").register(meterRegistry);
    }

    /** Relays one batch. @return rows handled: published, or parked as FAILED. */
    public int relayBatch() {
        Integer sent = claimTransaction.execute(status -> {
            List<OutboxRow> rows = jdbcTemplate.query(CLAIM, (rs, i) -> new OutboxRow(
                    rs.getLong("id"), rs.getString("topic"), rs.getString("message_key"),
                    rs.getString("payload_type"), rs.getString("payload"), rs.getString("trace_headers")),
                    properties.batchSize());
            int count = 0;
            for (OutboxRow row : rows) {
                if (!relay(row)) {
                    break;
                }
                count++;
            }
            return count;
        });
        return sent != null ? sent : 0;
    }

    /** @return false if relaying must stop for this poll (the row will be retried). */
    private boolean relay(OutboxRow row) {
        Object event;
        try {
            event = readPayload(row);
        } catch (RuntimeException | ClassNotFoundException e) {
            markFailed(row, "Unreadable payload: " + e);
            return true;
        }
        try {
            traceContext.callWithin(readHeaders(row), "outbox relay " + row.topic(), () -> {
                send(row, event);
                return null;
            });
        } catch (PermanentSendFailure e) {
            markFailed(row, e.getMessage());
            return true;
        } catch (RuntimeException e) {
            retries.increment();
            jdbcTemplate.update(MARK_RETRY, truncate(e.toString()), row.id());
            log.warn("Outbox send failed for event {} to {}; retrying on the next poll: {}", row.id(), row.topic(), e.toString());
            return false;
        }
        jdbcTemplate.update(MARK_PUBLISHED, Timestamp.from(clock.instant()), row.id());
        published.increment();
        return true;
    }

    private void send(OutboxRow row, Object event) {
        try {
            kafkaTemplate.send(row.topic(), row.key(), event)
                    .get(properties.sendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing outbox event " + row.id(), e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("Timed out publishing outbox event " + row.id(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SerializationException || cause instanceof RecordTooLargeException
                    || cause instanceof InvalidTopicException) {
                throw new PermanentSendFailure("Kafka rejected the record permanently: " + cause);
            }
            throw new IllegalStateException("Kafka send failed: " + cause, cause);
        } catch (SerializationException e) {
            throw new PermanentSendFailure("Record could not be serialized: " + e);
        }
    }

    private Object readPayload(OutboxRow row) throws ClassNotFoundException {
        if (!row.payloadType().startsWith(ALLOWED_PAYLOAD_PACKAGE)) {
            throw new IllegalArgumentException("Payload type outside " + ALLOWED_PAYLOAD_PACKAGE + ": " + row.payloadType());
        }
        Class<?> type = Class.forName(row.payloadType(), false, getClass().getClassLoader());
        return jsonMapper.readValue(row.payload(), type);
    }

    private Map<String, String> readHeaders(OutboxRow row) {
        if (row.traceHeaders() == null || row.traceHeaders().isBlank()) {
            return Map.of();
        }
        try {
            return jsonMapper.readValue(row.traceHeaders(), new TypeReference<Map<String, String>>() { });
        } catch (JacksonException e) {
            return Map.of(); // tracing is best effort; never block the event on it
        }
    }

    private void markFailed(OutboxRow row, String reason) {
        jdbcTemplate.update(MARK_FAILED, truncate(reason), row.id());
        log.error("ACTION REQUIRED: outbox event {} for topic {} can never be published and was marked FAILED: {}",
                row.id(), row.topic(), reason);
    }

    /** Deletes published rows older than the retention. @return rows deleted. */
    public int cleanup() {
        Timestamp cutoff = Timestamp.from(clock.instant().minus(properties.retention()));
        int total = 0;
        int deleted;
        do {
            deleted = jdbcTemplate.update(CLEANUP, cutoff);
            total += deleted;
        } while (deleted == 1000);
        return total;
    }

    void refreshGauges() {
        pending.set(0);
        failed.set(0);
        jdbcTemplate.query(COUNT, rs -> {
            String state = rs.getString(1);
            long count = rs.getLong(2);
            if ("PENDING".equals(state)) {
                pending.set(count);
            } else {
                failed.set(count);
            }
        });
    }

    private void poll() {
        try {
            for (int i = 0; i < MAX_BATCHES_PER_POLL; i++) {
                if (relayBatch() < properties.batchSize()) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            // Database briefly unavailable, lock wait timeout behind another instance's batch, ...: try again next poll.
            log.warn("Outbox relay poll failed: {}", e.toString());
        }
    }

    private void housekeeping() {
        try {
            refreshGauges();
        } catch (RuntimeException e) {
            log.debug("Outbox gauge refresh failed: {}", e.toString());
        }
    }

    private void scheduledCleanup() {
        try {
            int deleted = cleanup();
            if (deleted > 0) {
                log.info("Outbox cleanup deleted {} published events older than {}", deleted, properties.retention());
            }
        } catch (RuntimeException e) {
            log.warn("Outbox cleanup failed: {}", e.toString());
        }
    }

    @Override
    public void start() {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-relay");
            t.setDaemon(true);
            return t;
        });
        long poll = properties.pollInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::poll, poll, poll, TimeUnit.MILLISECONDS);
        scheduler.scheduleWithFixedDelay(this::housekeeping, 5_000, 15_000, TimeUnit.MILLISECONDS);
        long cleanup = properties.cleanupInterval().toMillis();
        scheduler.scheduleWithFixedDelay(this::scheduledCleanup, cleanup, cleanup, TimeUnit.MILLISECONDS);
        executor = scheduler;
        log.info("Outbox relay started (poll every {} ms, batch {})", poll, properties.batchSize());
    }

    /** Stops before the Kafka producer and the DataSource are closed (highest phase stops first). */
    @Override
    public void stop() {
        ScheduledExecutorService scheduler = executor;
        executor = null;
        if (scheduler == null) {
            return;
        }
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(properties.sendTimeout().toMillis() + 5_000, TimeUnit.MILLISECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return executor != null;
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 1000 ? value : value.substring(0, 1000);
    }

    private static final class PermanentSendFailure extends RuntimeException {
        PermanentSendFailure(String message) {
            super(message);
        }
    }
}
