package com.baldev.airline.payment.service.gateway;

import com.baldev.airline.common.exception.DownstreamServiceException;
import com.baldev.airline.payment.client.dto.UserDTO;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.payment.config.PaymentProperties;
import com.baldev.airline.payment.model.Payment;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Refund;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Adapter over the Razorpay SDK. Translates SDK exceptions into {@link DownstreamServiceException} and keeps
 * gateway-specific JSON out of the domain service. Replaced by {@link StubPaymentGateway} under the
 * {@code payment-stub} profile.
 */
@Slf4j
@Component
@Profile("!payment-stub")
@RequiredArgsConstructor
public class RazorpayGateway implements PaymentProvider {

    private final RazorpayClient razorpay;
    private final PaymentProperties properties;
    private final Clock clock;

    @Override
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

    @Override
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

    /** A payment link's entity lists the payments made through it as {@code payments: [{payment_id, ...}]}. */
    @Override
    public boolean isPaymentOfLink(String paymentLinkId, String providerPaymentId) {
        try {
            JSONArray payments = razorpay.paymentLink.fetch(paymentLinkId).toJson().optJSONArray("payments");
            if (payments == null) {
                return false;
            }
            for (int i = 0; i < payments.length(); i++) {
                JSONObject entry = payments.optJSONObject(i);
                if (entry != null && providerPaymentId.equals(entry.optString("payment_id", null))) {
                    return true;
                }
            }
            return false;
        } catch (RazorpayException e) {
            log.warn("Razorpay payment link fetch failed for {}", paymentLinkId, e);
            throw new DownstreamServiceException("Could not verify payment with the provider, please retry", e);
        }
    }

    /**
     * A payment link's entity has its {@code status} and the payments made through it
     * ({@code payments: [{payment_id, status, ...}]}); the captured one is the one that counts.
     */
    @Override
    public PaymentLinkState fetchPaymentLink(String paymentLinkId) {
        try {
            JSONObject link = razorpay.paymentLink.fetch(paymentLinkId).toJson();
            String paymentId = null;
            JSONArray payments = link.optJSONArray("payments");
            if (payments != null) {
                for (int i = 0; i < payments.length(); i++) {
                    JSONObject entry = payments.optJSONObject(i);
                    if (entry == null) continue;
                    if (paymentId == null || "captured".equalsIgnoreCase(entry.optString("status"))) {
                        paymentId = entry.optString("payment_id", null);
                    }
                }
            }
            return new PaymentLinkState(link.optString("status"), paymentId);
        } catch (RazorpayException e) {
            log.warn("Razorpay payment link fetch failed for {}", paymentLinkId, e);
            throw new DownstreamServiceException("Could not check payment status with the provider", e);
        }
    }

    @Override
    public RefundResult refund(String providerPaymentId, long amountMinor, String refundKey) {
        try {
            // Idempotency: Razorpay has no idempotency key for refunds, so look for an earlier attempt first.
            List<Refund> existing = razorpay.payments.fetchAllRefunds(providerPaymentId);
            for (Refund refund : existing) {
                JSONObject json = refund.toJson();
                JSONObject notes = json.optJSONObject("notes");
                if (notes != null && refundKey.equals(notes.optString("refund_key", null))) {
                    log.info("Refund {} already exists at Razorpay as {}", refundKey, json.optString("id"));
                    return new RefundResult(json.optString("id"), json.optString("status"));
                }
            }

            JSONObject request = new JSONObject();
            request.put("amount", amountMinor);
            request.put("speed", "normal");
            request.put("receipt", refundKey.length() > 40 ? refundKey.substring(0, 40) : refundKey);
            JSONObject notes = new JSONObject();
            notes.put("refund_key", refundKey);
            request.put("notes", notes);
            JSONObject created = razorpay.payments.refund(providerPaymentId, request).toJson();
            return new RefundResult(created.optString("id"), created.optString("status"));
        } catch (RazorpayException e) {
            if (isBadRequest(e)) {
                log.warn("Razorpay rejected refund {} for {}: {}", refundKey, providerPaymentId, e.getMessage());
                throw new RefundRejectedException(e.getMessage(), e);
            }
            log.warn("Razorpay refund {} for {} failed, will retry", refundKey, providerPaymentId, e);
            throw new DownstreamServiceException("Payment provider is unavailable, refund will be retried", e);
        }
    }

    @Override
    public RefundResult fetchRefund(String providerPaymentId, String providerRefundId) {
        try {
            JSONObject json = razorpay.payments.fetchRefund(providerPaymentId, providerRefundId).toJson();
            return new RefundResult(json.optString("id"), json.optString("status"));
        } catch (RazorpayException e) {
            throw new DownstreamServiceException("Could not check refund status with the provider", e);
        }
    }

    /** The SDK reports API errors as "<CODE>:<description>"; BAD_REQUEST_ERROR means the request itself is wrong. */
    private static boolean isBadRequest(RazorpayException e) {
        return e.getMessage() != null && e.getMessage().contains("BAD_REQUEST_ERROR");
    }

    @Override
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
