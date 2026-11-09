package com.baldev.airline.common.outbox;

import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.common.outbox.OutboxRelay.OutboxRow;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxRelayTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        OutboxProperties properties = new OutboxProperties(true, Duration.ofMillis(500), 100,
                Duration.ofSeconds(1), Duration.ofDays(7), Duration.ofHours(1));
        relay = new OutboxRelay(jdbc, tx, kafka, mapper, TraceContextCodec.NONE, properties,
                Clock.fixed(Instant.parse("2026-11-08T10:00:00Z"), ZoneOffset.UTC), registry);
    }

    @Test
    void publishesInOrderAndMarksEachRow() {
        claim(row(1, "a"), row(2, "b"));
        when(kafka.send(anyString(), anyString(), any())).thenReturn(sent());

        assertThat(relay.relayBatch()).isEqualTo(2);

        verify(jdbc).update(eq(OutboxRelay.MARK_PUBLISHED), any(), eq(1L));
        verify(jdbc).update(eq(OutboxRelay.MARK_PUBLISHED), any(), eq(2L));
        assertThat(registry.counter("outbox.published").count()).isEqualTo(2.0);
    }

    @Test
    void kafkaOutageStopsTheBatchSoLaterEventsCannotOvertake() {
        claim(row(1, "a"), row(2, "b"));
        when(kafka.send(anyString(), eq("1"), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        assertThat(relay.relayBatch()).isZero();

        verify(jdbc).update(eq(OutboxRelay.MARK_RETRY), anyString(), eq(1L));
        verify(kafka, never()).send(anyString(), eq("2"), any());
        verify(jdbc, never()).update(eq(OutboxRelay.MARK_FAILED), anyString(), any());
    }

    @Test
    void unpublishableRowsAreParkedAndDoNotBlockTheRest() {
        OutboxRow foreign = new OutboxRow(1, "payment.failed", "1", "java.net.URI", "\"http://x\"", null);
        OutboxRow tooLarge = row(2, "b");
        claim(foreign, tooLarge, row(3, "c"));
        when(kafka.send(anyString(), eq("2"), any())).thenReturn(CompletableFuture.failedFuture(new RecordTooLargeException("big")));
        when(kafka.send(anyString(), eq("3"), any())).thenReturn(sent());

        assertThat(relay.relayBatch()).isEqualTo(3);

        verify(jdbc).update(eq(OutboxRelay.MARK_FAILED), startsWith("Unreadable payload"), eq(1L));
        verify(jdbc).update(eq(OutboxRelay.MARK_FAILED), startsWith("Kafka rejected"), eq(2L));
        verify(jdbc).update(eq(OutboxRelay.MARK_PUBLISHED), any(), eq(3L));
    }

    private OutboxRow row(long id, String reason) {
        PaymentFailedEvent event = PaymentFailedEvent.builder().paymentId(id).bookingId(id).failureReason(reason).build();
        return new OutboxRow(id, "payment.failed", String.valueOf(id), PaymentFailedEvent.class.getName(),
                mapper.writeValueAsString(event), null);
    }

    @SuppressWarnings("unchecked")
    private void claim(OutboxRow... rows) {
        when(jdbc.query(eq(OutboxRelay.CLAIM), any(RowMapper.class), eq(100))).thenReturn(List.of(rows));
    }

    @SuppressWarnings("unchecked")
    private static CompletableFuture<SendResult<String, Object>> sent() {
        return CompletableFuture.completedFuture(mock(SendResult.class));
    }
}
