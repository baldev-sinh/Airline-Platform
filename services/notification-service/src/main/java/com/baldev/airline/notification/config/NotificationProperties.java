package com.baldev.airline.notification.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * {@code airline.notification.*}
 *
 * @param from          sender address of every e-mail
 * @param siteUrl       public address of the web app, for links in e-mails
 * @param emailEnabled  off: notifications are still recorded (inbox) but no e-mail is sent
 * @param maxAttempts   e-mail delivery attempts before a notification is given up as FAILED
 * @param retryInterval how often failed deliveries are retried (with exponential backoff per notification)
 * @param retention     notifications (which contain personal data) are deleted after this long
 */
@Validated
@ConfigurationProperties(prefix = "airline.notification")
public record NotificationProperties(
        @NotBlank @Email String from,
        @NotBlank String siteUrl,
        @DefaultValue("true") boolean emailEnabled,
        @DefaultValue("6") int maxAttempts,
        @DefaultValue("1m") Duration retryInterval,
        @DefaultValue("180d") Duration retention) {
}
