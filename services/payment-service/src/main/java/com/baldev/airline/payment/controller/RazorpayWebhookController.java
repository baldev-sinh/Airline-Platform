package com.baldev.airline.payment.controller;

import com.baldev.airline.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay server-to-server notifications. Public at the gateway (no JWT) because it is authenticated by the
 * HMAC signature over the raw body, which is why the body is bound as a String and never re-serialized.
 * Configure the webhook in the Razorpay dashboard for: payment_link.paid, payment_link.expired,
 * payment_link.cancelled.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/payments/webhooks")
public class RazorpayWebhookController {

    private final PaymentService paymentService;

    @PostMapping("/razorpay")
    public ResponseEntity<Void> razorpay(@RequestBody String rawBody,
                                         @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleRazorpayWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
