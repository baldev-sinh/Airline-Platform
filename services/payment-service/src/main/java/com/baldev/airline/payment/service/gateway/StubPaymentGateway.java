package com.baldev.airline.payment.service.gateway;

import com.baldev.airline.common.payload.dto.UserDTO;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.payment.config.PaymentProperties;
import com.baldev.airline.payment.model.Payment;
import com.baldev.airline.payment.repository.PaymentRepository;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline stand-in for Razorpay, for local and end-to-end testing without provider credentials.
 * Active only with the {@code payment-stub} Spring profile, which must never be enabled in production.
 *
 * <ul>
 *   <li>Payment links get the id {@code plink_STUB<paymentId>}; no network call is made.</li>
 *   <li>{@code fetchPayment} understands these ids:
 *     <ul>
 *       <li>{@code pay_STUBOK<paymentId>}: captured, full amount</li>
 *       <li>{@code pay_STUBLOW<paymentId>}: captured, 1 rupee short (exercises the amount check)</li>
 *       <li>{@code pay_STUBFAIL<paymentId>}: failed at the provider</li>
 *       <li>{@code pay_STUBWAIT<paymentId>}: authorized, not yet captured</li>
 *     </ul>
 *   </li>
 *   <li>Webhook signatures are verified for real (HMAC-SHA256 with {@code RAZORPAY_WEBHOOK_SECRET}), so a
 *       webhook test must sign its body exactly as Razorpay does.</li>
 * </ul>
 */
@Slf4j
@Component
@Profile("payment-stub")
@RequiredArgsConstructor
public class StubPaymentGateway implements PaymentProvider {

    private static final Pattern STUB_PAYMENT_ID = Pattern.compile("^pay_STUB(OK|LOW|FAIL|WAIT)(\\d+)$");

    private final PaymentRepository paymentRepository;
    private final PaymentProperties properties;

    @PostConstruct
    void warn() {
        log.warn("*** STUB PAYMENT GATEWAY ACTIVE: payments are simulated and never charged. "
                + "Never enable the 'payment-stub' profile outside local testing. ***");
    }

    @Override
    public CheckoutLink createPaymentLink(Payment payment, UserDTO user) {
        String linkId = "plink_STUB" + payment.getId();
        String url = properties.razorpay().callbackBaseUrl() + "/booking-success/" + payment.getBookingId()
                + "?razorpay_payment_id=pay_STUBOK" + payment.getId() + "&razorpay_payment_link_id=" + linkId;
        log.info("Stub payment link {} for paymentId={} amount={} {}", linkId, payment.getId(),
                payment.getAmount(), payment.getCurrency());
        return new CheckoutLink(linkId, url);
    }

    @Override
    public GatewayPayment fetchPayment(String providerPaymentId) {
        Matcher m = STUB_PAYMENT_ID.matcher(providerPaymentId == null ? "" : providerPaymentId);
        if (!m.matches()) {
            // Same outcome as a Razorpay payment that was not created by one of our links.
            return new GatewayPayment(providerPaymentId, "captured", 0, "INR", null);
        }
        String mode = m.group(1);
        Long paymentId = Long.valueOf(m.group(2));
        Payment payment = paymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            return new GatewayPayment(providerPaymentId, "captured", 0, "INR", null);
        }
        long amount = Money.toMinorUnits(payment.getAmount());
        String status = "captured";
        if ("LOW".equals(mode)) {
            amount -= 100;
        } else if ("FAIL".equals(mode)) {
            status = "failed";
        } else if ("WAIT".equals(mode)) {
            status = "authorized";
        }
        return new GatewayPayment(providerPaymentId, status, amount, payment.getCurrency(), String.valueOf(paymentId));
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
