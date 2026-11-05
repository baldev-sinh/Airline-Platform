package com.baldev.airline.booking.client;

import com.baldev.airline.common.payload.request.PaymentInitiateRequest;
import com.baldev.airline.common.payload.response.PaymentInitiateResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service", path = "/internal/payments")
public interface PaymentClient {

    @PostMapping
    PaymentInitiateResponse initiatePayment(@RequestBody PaymentInitiateRequest request);
}
