package com.baldev.airline.flightops.messaging;

import com.baldev.airline.common.event.FlightInstanceCreatedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes {@link FlightInstanceCreatedEvent} only after the creating transaction commits, so consumers never
 * see an instance that was rolled back. Keyed by flight instance id for per-instance ordering.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlightInstanceEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFlightInstanceCreated(FlightInstanceCreatedEvent event) {
        kafkaTemplate.send(KafkaTopics.FLIGHT_INSTANCE_CREATED, String.valueOf(event.getFlightInstanceId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish FlightInstanceCreated for flightInstanceId={}",
                                event.getFlightInstanceId(), ex);
                    } else {
                        log.info("Published FlightInstanceCreated flightInstanceId={}", event.getFlightInstanceId());
                    }
                });
    }
}
