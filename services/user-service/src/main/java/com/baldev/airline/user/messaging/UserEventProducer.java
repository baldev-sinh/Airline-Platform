package com.baldev.airline.user.messaging;

import com.baldev.airline.common.event.UserRegisteredEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.common.outbox.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Records user events in the transactional outbox inside the signup transaction, keyed by user id. */
@Component
@RequiredArgsConstructor
public class UserEventProducer {

    private final OutboxPublisher outbox;

    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        outbox.publish(KafkaTopics.USER_REGISTERED, String.valueOf(event.getUserId()), event);
    }
}
