package com.baldev.airline.payment.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One refund of (part of) a captured payment.
 *
 * <pre>
 *   REQUESTED --claim--> SUBMITTING --provider accepted--> PROCESSING --provider done--> PROCESSED
 *       ^                     |                                  |
 *       +--transient error----+          provider rejected/failed +-------------------> FAILED
 * </pre>
 *
 * {@code refundKey} comes from the requester and is unique, so a redelivered request never refunds twice; it is
 * also stored on the provider's refund (notes), so a retry after an unknown outcome finds the first attempt.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "refund",
        indexes = {
                @Index(name = "idx_refund_payment", columnList = "paymentId"),
                @Index(name = "idx_refund_status_updated", columnList = "status, updatedAt")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refund_key", columnNames = "refundKey"),
                @UniqueConstraint(name = "uk_refund_provider_refund_id", columnNames = "providerRefundId")
        })
public class Refund {

    public enum State { REQUESTED, SUBMITTING, PROCESSING, PROCESSED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String refundKey;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private State status;

    /** Provider refund id (Razorpay rfnd_...), set once the provider accepted the refund. */
    @Column(length = 64)
    private String providerRefundId;

    @Column(length = 255)
    private String reason;

    @Column(length = 500)
    private String failureReason;

    @Builder.Default
    @Column(nullable = false)
    private int attempts = 0;

    private Instant processedAt;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public boolean isTerminal() {
        return status == State.PROCESSED || status == State.FAILED;
    }
}
