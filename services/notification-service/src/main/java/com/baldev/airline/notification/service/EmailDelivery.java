package com.baldev.airline.notification.service;

import com.baldev.airline.notification.config.NotificationProperties;
import com.baldev.airline.notification.model.Notification;
import com.baldev.airline.notification.model.Notification.Channel;
import com.baldev.airline.notification.model.Notification.Status;
import com.baldev.airline.notification.repository.NotificationRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Sends recorded e-mails over SMTP, with retries.
 *
 * <p>Each attempt first claims the notification (moves its next attempt time two minutes ahead), so the Kafka
 * consumer and the retry job, on any number of instances, never send the same e-mail twice at once. A failure is
 * retried with exponential backoff ({@code retry-interval} x 2^attempt, at most one hour) until
 * {@code max-attempts}, then the notification is marked FAILED and counted in {@code notifications.failed}.
 */
@Slf4j
@Component
public class EmailDelivery {

    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);

    private final NotificationRepository repository;
    private final JavaMailSender mailSender;
    private final NotificationProperties properties;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public EmailDelivery(NotificationRepository repository, JavaMailSender mailSender, NotificationProperties properties,
                         TransactionTemplate transactionTemplate, Clock clock, MeterRegistry meterRegistry) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    public void deliver(Long notificationId) {
        Instant now = clock.instant();
        Integer claimed = transactionTemplate.execute(s -> repository.claim(notificationId, now, now.plus(LEASE)));
        if (claimed == null || claimed == 0) {
            return;
        }
        Notification notification = repository.findById(notificationId).orElse(null);
        if (notification == null) {
            return;
        }
        if (!properties.emailEnabled()) {
            finish(notificationId, Status.SKIPPED, "E-mail disabled", null);
            return;
        }
        try {
            mailSender.send(toMime(notification));
            finish(notificationId, Status.SENT, null, clock.instant());
            meterRegistry.counter("notifications.sent", "channel", "email", "type", notification.getType()).increment();
            log.info("E-mail {} ({}) sent for {}", notification.getId(), notification.getType(), notification.getEventKey());
        } catch (MailException | MessagingException e) {
            retryOrGiveUp(notification, e);
        }
    }

    private MimeMessage toMime(Notification notification) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
        helper.setFrom(properties.from());
        helper.setTo(notification.getRecipient());
        helper.setSubject(notification.getSubject());
        // Plain-text part for clients that do not render HTML, then the HTML part.
        helper.setText(notification.getSummary(), notification.getBody());
        return mime;
    }

    private void retryOrGiveUp(Notification notification, Exception e) {
        int attempts = notification.getAttempts();
        String error = e.getClass().getSimpleName() + ": " + e.getMessage();
        if (attempts >= properties.maxAttempts()) {
            finish(notification.getId(), Status.FAILED, error, null);
            meterRegistry.counter("notifications.failed", "channel", "email", "type", notification.getType()).increment();
            log.error("ACTION REQUIRED: e-mail {} for {} failed {} times, giving up: {}", notification.getId(),
                    notification.getEventKey(), attempts, error);
            return;
        }
        Duration backoff = properties.retryInterval().multipliedBy(1L << Math.min(attempts - 1, 16));
        Instant next = clock.instant().plus(backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff);
        transactionTemplate.executeWithoutResult(s -> repository.findById(notification.getId()).ifPresent(n -> {
            n.setNextAttemptAt(next);
            n.setLastError(truncate(error));
        }));
        log.warn("E-mail {} attempt {} failed, retrying at {}: {}", notification.getId(), attempts, next, error);
    }

    private void finish(Long id, Status status, String error, Instant sentAt) {
        transactionTemplate.executeWithoutResult(s -> repository.findById(id).ifPresent(n -> {
            n.setStatus(status);
            n.setLastError(truncate(error));
            n.setSentAt(sentAt);
            n.setNextAttemptAt(null);
        }));
    }

    @Scheduled(fixedDelayString = "${airline.notification.retry-interval:1m}", initialDelayString = "PT30S")
    public void retryDue() {
        List<Long> due = repository.findDue(Channel.EMAIL, Status.PENDING, clock.instant(), PageRequest.of(0, 50));
        for (Long id : due) {
            try {
                deliver(id);
            } catch (RuntimeException e) {
                log.warn("Retrying e-mail {} failed: {}", id, e.getMessage());
            }
        }
    }

    /** Notifications contain personal data (names, addresses, itineraries): keep them only as long as useful. */
    @Scheduled(cron = "${airline.notification.retention-cron:0 30 3 * * *}")
    public void purgeOld() {
        Integer deleted = transactionTemplate.execute(s ->
                repository.deleteCreatedBefore(clock.instant().minus(properties.retention())));
        if (deleted != null && deleted > 0) {
            log.info("Deleted {} notifications older than {}", deleted, properties.retention());
        }
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 500 ? value : value.substring(0, 500);
    }
}
