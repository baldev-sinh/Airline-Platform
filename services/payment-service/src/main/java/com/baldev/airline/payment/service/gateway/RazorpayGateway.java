package com.baldev.airline.payment.service.gateway;

import com.baldev.airline.common.exception.DownstreamServiceException;
import com.baldev.airline.common.payload.dto.UserDTO;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.payment.config.PaymentProperties;
import com.baldev.airline.payment.model.Payment;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Adapter over the Razorpay SDK. Translates SDK exceptions into {@link DownstreamServiceException} and keeps
 * gateway-specific JSON out of the domain service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RazorpayGateway {

    private final RazorpayClient razorpay;
    private final PaymentProperties properties;
    private final Clock clock;

    /** Gateway's view of a payment, as fetched with our API credentials (never trusted from the client). */
    public record GatewayPayment(String id, String status, long amountMinor, String currency, String notesPaymentId) {

        public boolean isCaptured() {
            return "captured".equalsIgnoreCase(status);
        }
    }

    public record CheckoutLink(String id, String url) {
    }

    public CheckoutLink createPaymentLink(Payment payment, UserDTO user) {
        JSONObject request = new JSONObject();
        request.put("amount", Money.toMinorUnits(payment.getAmount()));
        request.put("currency", payment.getCurrency());
        request.put("description", "Booking payment " + payment.getTransactionId());
        // reference_id must be unique per link: reusing our transaction id makes link creation idempotent.
        request.put("reference_id", payment.getTransactionId());
        request.put("expire_by", clock.instant().plus(properties.linkExpiry()).getEpochSecond());

        JSONObject customer = new JSONObject();
        customer.put("name", user.getFullName());
        customer.put("email", user.getEmail());
        if (user.getPhone() != null && !user.getPhone().isBlank()) {
            customer.put("contact", user.getPhone());
        }
        request.put("customer", customer);

        JSONObject notify = new JSONObject();
        notify.put("email", true);
        notify.put("sms", user.getPhone() != null && !user.getPhone().isBlank());
        request.put("notify", notify);
        request.put("reminder_enable", false);

        request.put("callback_url", properties.razorpay().callbackBaseUrl() + "/booking-success/" + payment.getBookingId());
        request.put("callback_method", "get");

        JSONObject notes = new JSONObject();
        notes.put("payment_id", String.valueOf(payment.getId()));
        notes.put("booking_id", String.valueOf(payment.getBookingId()));
        request.put("notes", notes);

        try {
            PaymentLink link = razorpay.paymentLink.create(request);
            return new CheckoutLink(link.get("id"), link.get("short_url"));
        } catch (RazorpayException e) {
            log.error("Razorpay payment link creation failed for paymentId={}", payment.getId(), e);
            throw new DownstreamServiceException("Payment provider is unavailable, please retry", e);
        }
    }

    public GatewayPayment fetchPayment(String providerPaymentId) {
        try {
            JSONObject json = razorpay.payments.fetch(providerPaymentId).toJson();
            JSONObject notes = json.optJSONObject("notes");
            return new GatewayPayment(
                    json.optString("id"),
                    json.optString("status"),
                    json.optLong("amount"),
                    json.optString("currency"),
                    notes != null ? notes.optString("payment_id", null) : null);
        } catch (RazorpayException e) {
            log.warn("Razorpay payment fetch failed for {}", providerPaymentId, e);
            throw new DownstreamServiceException("Could not verify payment with the provider, please retry", e);
        }
    }

    /** Verifies the X-Razorpay-Signature header (HMAC-SHA256 of the raw body with the webhook secret). */
    public boolean isValidWebhookSignature(String rawBody, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        try {
            return Utils.verifyWebhookSignature(rawBody, signature, properties.razorpay().webhookSecret());
        } catch (RazorpayException e) {
            return false;
        }
    }
}
