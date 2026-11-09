package com.baldev.airline.payment.service.gateway;

import com.baldev.airline.payment.client.dto.UserDTO;
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

    /**
     * The provider's view of a payment link. {@code status} is the provider's own value
     * (Razorpay: created, partially_paid, paid, expired, cancelled); {@code paymentId} is the payment made
     * through the link, if any.
     */
    record PaymentLinkState(String status, String paymentId) {

        public boolean isPaid() {
            return "paid".equalsIgnoreCase(status);
        }

        /** The link can no longer be paid. */
        public boolean isClosed() {
            return "expired".equalsIgnoreCase(status) || "cancelled".equalsIgnoreCase(status);
        }
    }

    /**
     * The provider's view of a refund. {@code status}: Razorpay pending, processed or failed.
     */
    record RefundResult(String id, String status) {

        public boolean isProcessed() {
            return "processed".equalsIgnoreCase(status);
        }

        public boolean isFailed() {
            return "failed".equalsIgnoreCase(status);
        }
    }

    /** The provider refused a refund for good (e.g. more than the captured amount): retrying cannot help. */
    class RefundRejectedException extends RuntimeException {
        public RefundRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    CheckoutLink createPaymentLink(Payment payment, UserDTO user);

    GatewayPayment fetchPayment(String providerPaymentId);

    /**
     * Asks the provider whether {@code providerPaymentId} was made through payment link {@code paymentLinkId}.
     * Lets the browser-verify path find our payment by link id without trusting the client's word for it.
     */
    boolean isPaymentOfLink(String paymentLinkId, String providerPaymentId);

    /** Asks the provider what happened to a payment link (used to reconcile payments left pending). */
    PaymentLinkState fetchPaymentLink(String paymentLinkId);

    /**
     * Refunds {@code amountMinor} of a captured payment. Idempotent per {@code refundKey}: if a refund with this key
     * already exists at the provider (an earlier attempt whose response was lost), that one is returned instead of
     * creating a second refund.
     *
     * @throws RefundRejectedException if the provider refuses the refund permanently
     * @throws com.baldev.airline.common.exception.DownstreamServiceException if the provider is unreachable (retry)
     */
    RefundResult refund(String providerPaymentId, long amountMinor, String refundKey);

    /** Current state of a refund at the provider (used to reconcile refunds still pending there). */
    RefundResult fetchRefund(String providerPaymentId, String providerRefundId);

    /** Verifies the X-Razorpay-Signature header (HMAC-SHA256 of the raw body with the webhook secret). */
    boolean isValidWebhookSignature(String rawBody, String signature);
}
