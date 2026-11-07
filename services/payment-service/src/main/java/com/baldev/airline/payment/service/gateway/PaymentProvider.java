package com.baldev.airline.payment.service.gateway;

import com.baldev.airline.common.payload.dto.UserDTO;
import com.baldev.airline.payment.model.Payment;

/**
 * The payment provider as seen by {@code PaymentServiceImpl}. Production uses {@link RazorpayGateway};
 * local testing without provider credentials can use {@link StubPaymentGateway} (profile {@code payment-stub}).
 */
public interface PaymentProvider {

    /** Provider's view of a payment, as fetched with our API credentials (never trusted from the client). */
    record GatewayPayment(String id, String status, long amountMinor, String currency, String notesPaymentId) {

        public boolean isCaptured() {
            return "captured".equalsIgnoreCase(status);
        }
    }

    record CheckoutLink(String id, String url) {
    }

    CheckoutLink createPaymentLink(Payment payment, UserDTO user);

    GatewayPayment fetchPayment(String providerPaymentId);

    /**
     * Asks the provider whether {@code providerPaymentId} was made through payment link {@code paymentLinkId}.
     * Lets the browser-verify path find our payment by link id without trusting the client's word for it.
     */
    boolean isPaymentOfLink(String paymentLinkId, String providerPaymentId);

    /** Verifies the X-Razorpay-Signature header (HMAC-SHA256 of the raw body with the webhook secret). */
    boolean isValidWebhookSignature(String rawBody, String signature);
}
