package com.baldev.service.gateway;

import com.baldev.model.Payment;
import com.baldev.payload.dto.UserDTO;
import com.baldev.payload.response.PaymentLinkResponse;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class RazorpayService {

    @Value("${razorpay.api.key}")
    private String razorpayKeyId;

    @Value("${razorpay.api.secret}")
    private String razorpaySecret;

    @Value("${razorpay.callback.base-url}")
    private String callbackBaseUrl;

    public PaymentLinkResponse createPaymentLink(UserDTO user, Payment payment) throws RazorpayException {

        RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpaySecret);

        BigDecimal amount = BigDecimal.valueOf(payment.getAmount());

        Long amountInPaisa = amount.multiply(new BigDecimal("100")).longValue();

        JSONObject paymentLinkRequest = new JSONObject();
        paymentLinkRequest.put("amount", amountInPaisa);
        paymentLinkRequest.put("currency", "INR");
        paymentLinkRequest.put("description", payment.getTransactionId());

//        customer details
        JSONObject customer = new JSONObject();
        customer.put("name", user.getFullName());
        customer.put("email", user.getEmail());
        if (user.getPhone() != null) {
            customer.put("contanct", user.getPhone());
        }

        paymentLinkRequest.put("customer", customer);

//        notification settings
        JSONObject notify = new JSONObject();
        notify.put("email", true);
        notify.put("sms", user.getPhone() != null);
        paymentLinkRequest.put("notify", notify);

//        enable reminders
        paymentLinkRequest.put("reminder_enable", true);

//        callback configuration
        String successUrl = callbackBaseUrl + "/booking-success/" + payment.getBookingId();

        paymentLinkRequest.put("callback_url", successUrl);
        paymentLinkRequest.put("callback_method", "get");

//        additional metadata for traking
        JSONObject notes = new JSONObject();
        notes.put("user_id", user.getId());
        notes.put("payment_id", payment.getId());
        notes.put("booking_id", payment.getBookingId());

        paymentLinkRequest.put("notes", notes);

//        create payment link
        PaymentLink paymentLink = razorpay.paymentLink.create(paymentLinkRequest);

        String paymentUrl = paymentLink.get("short_url");
        String paymentLinkId = paymentLink.get("id");

        PaymentLinkResponse response = PaymentLinkResponse.builder()
                .payment_link_id(paymentLinkId)
                .payment_link_url(paymentUrl)
                .build();

        return response;

    }

    public JSONObject fetchPaymentDetails(String paymentId) throws RazorpayException {
        RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpaySecret);
        com.razorpay.Payment payment = razorpay.payments.fetch(paymentId);
        return payment.toJson();
    }


}
