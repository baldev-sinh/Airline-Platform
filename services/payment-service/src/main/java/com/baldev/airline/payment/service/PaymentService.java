package com.baldev.airline.payment.service;

import com.baldev.airline.payment.payload.dto.PaymentDTO;
import com.baldev.airline.payment.payload.request.PaymentInitiateRequest;
import com.baldev.airline.payment.payload.request.PaymentVerifyRequest;
import com.baldev.airline.payment.payload.response.PaymentInitiateResponse;
import com.baldev.airline.common.web.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public interface PaymentService {

    /** Internal: creates (or reuses) the pending payment for a booking and a gateway checkout link. */
    PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request);

    /** Client callback after checkout; re-verifies with the gateway before settling. */
    PaymentDTO verifyPayment(CurrentUser user, PaymentVerifyRequest request);

    /** Gateway webhook; authenticated by HMAC signature, not by user identity. */
    void handleRazorpayWebhook(String rawBody, String signature);

    PaymentDTO getPayment(CurrentUser user, Long paymentId);

    Page<PaymentDTO> getAllPayments(Pageable pageable);

    /** Latest payment per booking, keyed by booking id. */
    Map<Long, PaymentDTO> getPaymentsByBookingIds(List<Long> bookingIds);

    /** What a reconciliation of one payment did. */
    enum ReconcileOutcome { SETTLED, FAILED, STILL_OPEN, SKIPPED }

    /** Ids of payments still pending that were created between {@code maxAge} and {@code minAge} ago. */
    List<Long> findPendingForReconciliation(Duration minAge, Duration maxAge, int limit);

    /**
     * Asks the provider what happened to a pending payment and settles or fails it, exactly as the browser
     * verify and the webhook would. Safety net for payments neither of those reported (closed tab, missed
     * webhook). Safe to run at any time and in parallel: settled payments are left alone.
     */
    ReconcileOutcome reconcile(Long paymentId);
}
