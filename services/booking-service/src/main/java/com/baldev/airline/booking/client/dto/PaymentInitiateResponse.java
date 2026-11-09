package com.baldev.airline.booking.client.dto;

import java.math.BigDecimal;

import com.baldev.airline.common.enums.PaymentGateway;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Client-side copy of payment-service's API model; only this service's view of the contract. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentInitiateResponse {

    private Long paymentId;
    private PaymentGateway gateway;
    private String transactionId;

    // Razorpay specific fields
    private String razorpayOrderId;

    private BigDecimal amount;

    private String description;

    // Frontend should redirect user to this URL for payment
    private String checkoutUrl;

    private String message;
    private Boolean success;

}
