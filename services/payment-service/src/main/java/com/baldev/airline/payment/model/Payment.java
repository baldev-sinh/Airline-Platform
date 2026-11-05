package com.baldev.airline.payment.model;

import com.baldev.airline.common.enums.PaymentGateway;
import com.baldev.airline.common.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long bookingId;

    private Double amount;

    @Enumerated(EnumType.STRING)
    private PaymentGateway provider;

    private String providerPaymentId;
    private String transactionId;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String failureReason;
    private LocalDateTime paidAt;
    private String refundId;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
