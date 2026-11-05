package com.baldev.airline.booking.client;

import com.baldev.airline.common.payload.request.PaymentInitiateRequest;
import com.baldev.airline.common.payload.response.PaymentInitiateResponse;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("payment-service")
public interface PaymentClient {

    @PostMapping("/api/payments/initiate")
    PaymentInitiateResponse initiatePayment(
            @Valid @RequestBody PaymentInitiateRequest paymentInitiateRequest
    );
}
