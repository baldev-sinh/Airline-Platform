package com.baldev.airline.payment.mapper;

import com.baldev.airline.payment.payload.dto.PaymentDTO;
import com.baldev.airline.payment.model.Payment;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentDTO toDTO(Payment payment) {
        if (payment == null) {
            return null;
        }
        PaymentDTO dto = new PaymentDTO();
        dto.setId(payment.getId());
        dto.setUserId(payment.getUserId());
        dto.setBookingId(payment.getBookingId());
        dto.setGateway(payment.getProvider());
        dto.setAmount(payment.getAmount());
        dto.setTransactionId(payment.getTransactionId());
        dto.setGatewayOrderId(payment.getProviderOrderId());
        dto.setGatewayPaymentId(payment.getProviderPaymentId());
        dto.setStatus(payment.getStatus());
        dto.setFailureReason(payment.getFailureReason());
        dto.setInitiatedAt(toUtc(payment.getCreatedAt()));
        dto.setCreatedAt(toUtc(payment.getCreatedAt()));
        dto.setCompletedAt(toUtc(payment.getPaidAt()));
        dto.setUpdatedAt(toUtc(payment.getUpdatedAt()));
        return dto;
    }

    private static LocalDateTime toUtc(Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneOffset.UTC) : null;
    }
}
