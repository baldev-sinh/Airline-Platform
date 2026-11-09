package com.baldev.airline.flightops.client.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Client-side copy of pricing-service's API model; only this service's view of the contract. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FareRulesResponse {
    private Long id;
    private String ruleName;
    private Long fareId;
    private Long airlineId;
    private Boolean isRefundable;
    private BigDecimal changeFee;
    private BigDecimal cancellationFee;
    private Integer refundDeadlineDays;
    private Integer changeDeadlineHours;
    private Boolean isChangeable;
    private Instant createdAt;
    private Instant updatedAt;
}
