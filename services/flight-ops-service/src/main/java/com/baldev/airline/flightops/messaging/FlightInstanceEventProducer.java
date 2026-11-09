package com.baldev.airline.flightops.messaging;

import com.baldev.airline.common.event.FlightInstanceCreatedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.common.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Records {@link FlightInstanceCreatedEvent} in the transactional outbox inside the creating transaction, keyed by
 * flight instance id. seat-service builds the instance's seat inventory from it, so losing one would leave a
 * bookable flight without seats; the outbox guarantees it is published once the instance has committed.
 */
@Component
@RequiredArgsConstructor
public class FlightInstanceEventProducer {

    private final OutboxPublisher outbox;

    @EventListener
    public void onFlightInstanceCreated(FlightInstanceCreatedEvent event) {
        outbox.publish(KafkaTopics.FLIGHT_INSTANCE_CREATED, String.valueOf(event.getFlightInstanceId()), event);
    }
}
