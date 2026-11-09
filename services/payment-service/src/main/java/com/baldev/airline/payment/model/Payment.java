package com.baldev.airline.payment.model;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import com.baldev.airline.common.enums.PaymentGateway;
import com.baldev.airline.common.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        indexes = @Index(name = "idx_payment_booking", columnList = "bookingId"),
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_payment_transaction_id", columnNames = "transactionId"),
                // One gateway payment can settle at most one of our payments (blocks replaying a payment id).
                @UniqueConstraint(name = "uk_payment_provider_payment_id", columnNames = "providerPaymentId"),
                @UniqueConstraint(name = "uk_payment_provider_order_id", columnNames = "providerOrderId")
        })
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long bookingId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentGateway provider;

    /** Gateway-side checkout object (Razorpay payment link id). */
    @Column(length = 64)
    private String providerOrderId;

    /** Hosted checkout URL for the open link; lets a retried checkout reuse it instead of creating another. */
    @Column(length = 255)
    private String checkoutUrl;

    /** Gateway-side payment id, set once captured. */
    @Column(length = 64)
    private String providerPaymentId;

    @Column(nullable = false, length = 64)
    private String transactionId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(length = 500)
    private String failureReason;

    private Instant paidAt;

    @Column(length = 64)
    private String refundId;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    public boolean isTerminal() {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.FAILED
                || status == PaymentStatus.CANCELLED || status == PaymentStatus.REFUNDED
                || status == PaymentStatus.PARTIALLY_REFUNDED;
    }

    /** Captured money that can (still) be refunded. */
    public boolean isRefundable() {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.PARTIALLY_REFUNDED;
    }
}
