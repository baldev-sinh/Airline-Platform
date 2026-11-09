package com.baldev.airline.notification.service;

import com.baldev.airline.notification.model.Notification;
import com.baldev.airline.notification.model.Notification.Channel;
import com.baldev.airline.notification.model.Notification.Status;
import com.baldev.airline.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Clock;
import java.util.Locale;

/**
 * Records a notification for every channel (idempotent per event key and channel) and hands e-mails to
 * {@link EmailDelivery}. The Kafka consumer never waits for SMTP to recover: a failed e-mail stays PENDING and
 * is retried by the delivery job, so one mail outage cannot stall a partition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository repository;
    private final TemplateEngine templateEngine;
    private final EmailDelivery emailDelivery;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public void notify(NotificationMessage message) {
        Long emailId;
        try {
            emailId = transactionTemplate.execute(status -> record(message));
        } catch (DataIntegrityViolationException e) {
            log.info("Notification {} recorded concurrently by another consumer", message.eventKey());
            return;
        }
        if (emailId != null) {
            emailDelivery.deliver(emailId);
        }
    }

    /** @return the id of a new e-mail to deliver now, if any. */
    private Long record(NotificationMessage message) {
        if (message.userId() != null && !repository.existsByEventKeyAndChannel(message.eventKey(), Channel.IN_APP)) {
            repository.save(Notification.builder()
                    .eventKey(message.eventKey())
                    .channel(Channel.IN_APP)
                    .type(message.type())
                    .userId(message.userId())
                    .subject(truncate(message.subject(), 200))
                    .summary(truncate(message.summary(), 1000))
                    .status(Status.SENT)
                    .sentAt(clock.instant())
                    .build());
        }
        if (repository.existsByEventKeyAndChannel(message.eventKey(), Channel.EMAIL)) {
            log.info("Duplicate event {}: e-mail already recorded", message.eventKey());
            return null;
        }
        boolean hasAddress = message.email() != null && !message.email().isBlank();
        Notification email = repository.save(Notification.builder()
                .eventKey(message.eventKey())
                .channel(Channel.EMAIL)
                .type(message.type())
                .userId(message.userId())
                .recipient(hasAddress ? message.email().trim() : null)
                .subject(truncate(message.subject(), 200))
                .summary(truncate(message.summary(), 1000))
                .body(hasAddress ? render(message) : null)
                .status(hasAddress ? Status.PENDING : Status.SKIPPED)
                .lastError(hasAddress ? null : "No e-mail address")
                .nextAttemptAt(clock.instant())
                .build());
        return hasAddress ? email.getId() : null;
    }

    private String render(NotificationMessage message) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariables(message.variables());
        context.setVariable("subject", message.subject());
        return templateEngine.process("email/" + message.type(), context);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
