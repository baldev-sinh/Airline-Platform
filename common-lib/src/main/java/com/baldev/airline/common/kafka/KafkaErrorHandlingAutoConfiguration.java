package com.baldev.airline.common.kafka;

import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import jakarta.validation.ValidationException;
import org.apache.kafka.clients.admin.NewTopic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumer error handling for every Kafka-enabled service: a failed record is retried with exponential backoff,
 * then published to {@code <topic>-dlt} so one poison message never blocks a partition. Errors that cannot
 * succeed on retry (validation, missing entity) go to the DLT immediately.
 *
 * <p>Boot's listener container factory picks up the {@link CommonErrorHandler} bean automatically.
 */
@AutoConfiguration(afterName = "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration")
@ConditionalOnClass(KafkaOperations.class)
@ConditionalOnBean(KafkaOperations.class)
public class KafkaErrorHandlingAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(CommonErrorHandler.class)
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaOperations<?, ?> kafkaOperations,
            @Value("${airline.kafka.retry.max-attempts:4}") int maxRetries,
            @Value("${airline.kafka.retry.initial-interval-ms:1000}") long initialInterval) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaOperations);
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(30_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler((record, ex) -> {
            log.error("Sending record to DLT after retries: topic={} partition={} offset={} key={}",
                    record.topic(), record.partition(), record.offset(), record.key(), ex);
            recoverer.accept(record, ex);
        }, backOff);
        handler.addNotRetryableExceptions(
                ValidationException.class,
                ResourceNotFoundException.class,
                BusinessRuleException.class,
                IllegalArgumentException.class);
        return handler;
    }

    /**
     * Declares every platform topic and its dead-letter topic so brokers can run with
     * {@code auto.create.topics.enable=false}. Creation is idempotent; existing topics are left untouched.
     */
    @Bean
    @ConditionalOnBean(KafkaAdmin.class)
    public KafkaAdmin.NewTopics airlineTopics(
            @Value("${airline.kafka.topics.partitions:3}") int partitions,
            @Value("${airline.kafka.topics.replication-factor:1}") short replicationFactor) {
        List<NewTopic> topics = new ArrayList<>();
        for (String name : List.of(KafkaTopics.FLIGHT_INSTANCE_CREATED, KafkaTopics.PAYMENT_COMPLETED,
                KafkaTopics.PAYMENT_FAILED, KafkaTopics.BOOKING_CONFIRMED, KafkaTopics.BOOKING_CANCELLED)) {
            topics.add(TopicBuilder.name(name).partitions(partitions).replicas(replicationFactor).build());
            topics.add(TopicBuilder.name(name + "-dlt").partitions(partitions).replicas(replicationFactor).build());
        }
        return new KafkaAdmin.NewTopics(topics.toArray(NewTopic[]::new));
    }
}
