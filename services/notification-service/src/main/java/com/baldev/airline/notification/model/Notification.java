package com.baldev.airline.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One notification to one customer on one channel. {@code (eventKey, channel)} is unique, so a redelivered Kafka
 * event never notifies twice.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notification",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_event_channel", columnNames = {"eventKey", "channel"}),
        indexes = {
                @Index(name = "idx_notification_user_created", columnList = "userId, createdAt"),
                @Index(name = "idx_notification_status_next", columnList = "status, nextAttemptAt")
        })
public class Notification {

    public enum Channel { EMAIL, IN_APP }

    public enum Status {
        /** Waiting for (another) delivery attempt. */
        PENDING,
        SENT,
        /** Gave up after the maximum number of attempts. */
        FAILED,
        /** Not sent on purpose (no address, channel disabled). */
        SKIPPED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** {@code <topic>:<aggregate id>[:<discriminator>]}, e.g. {@code booking.confirmed:42}. */
    @Column(nullable = false, length = 150)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Channel channel;

    /** Template name, e.g. {@code booking-confirmed}. */
    @Column(nullable = false, length = 50)
    private String type;

    private Long userId;

    /** E-mail address for EMAIL; empty for IN_APP. */
    @Column(length = 255)
    private String recipient;

    @Column(nullable = false, length = 200)
    private String subject;

    /** Short plain-text summary shown in the inbox. */
    @Column(nullable = false, length = 1000)
    private String summary;

    /** Rendered HTML body (EMAIL only). */
    @Lob
    @Column(columnDefinition = "mediumtext")
    private String body;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private Status status;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    private Instant nextAttemptAt;

    @Column(length = 500)
    private String lastError;

    private Instant sentAt;

    /** IN_APP: when the customer opened it. */
    private Instant readAt;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
