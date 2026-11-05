package com.baldev.airline.payment.service;

import com.baldev.airline.common.payload.dto.PaymentDTO;
import com.baldev.airline.common.payload.request.PaymentInitiateRequest;
import com.baldev.airline.common.payload.request.PaymentVerifyRequest;
import com.baldev.airline.common.payload.response.PaymentInitiateResponse;
import com.baldev.airline.common.web.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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
}
