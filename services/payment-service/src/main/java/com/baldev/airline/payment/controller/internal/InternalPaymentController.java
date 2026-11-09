package com.baldev.airline.payment.controller.internal;

import com.baldev.airline.payment.payload.dto.PaymentDTO;
import com.baldev.airline.payment.payload.request.PaymentInitiateRequest;
import com.baldev.airline.payment.payload.response.PaymentInitiateResponse;
import com.baldev.airline.payment.service.PaymentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Service-to-service payment API used by booking-service. Never routed by the API gateway. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/payments")
public class InternalPaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public PaymentInitiateResponse initiate(@Valid @RequestBody PaymentInitiateRequest request) {
        return paymentService.initiatePayment(request);
    }

    @PostMapping("/by-bookings")
    public Map<Long, PaymentDTO> byBookings(@RequestBody @Size(max = 500) List<Long> bookingIds) {
        return paymentService.getPaymentsByBookingIds(bookingIds);
    }
}
