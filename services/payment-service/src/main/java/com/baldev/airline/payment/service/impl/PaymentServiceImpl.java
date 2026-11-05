package com.baldev.airline.payment.service.impl;

import com.baldev.airline.payment.client.UserClient;
import com.baldev.airline.common.enums.PaymentGateway;
import com.baldev.airline.common.enums.PaymentStatus;
import com.baldev.airline.payment.messaging.PaymentEventProducer;
import com.baldev.airline.payment.mapper.PaymentMapper;
import com.baldev.airline.payment.model.Payment;
import com.baldev.airline.common.payload.dto.PaymentDTO;
import com.baldev.airline.common.payload.dto.UserDTO;
import com.baldev.airline.common.payload.request.PaymentInitiateRequest;
import com.baldev.airline.common.payload.request.PaymentVerifyRequest;
import com.baldev.airline.common.payload.response.PaymentInitiateResponse;
import com.baldev.airline.common.payload.response.PaymentLinkResponse;
import com.baldev.airline.payment.repository.PaymentRepository;
import com.baldev.airline.payment.service.PaymentService;
import com.baldev.airline.payment.service.gateway.RazorpayService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {


    private final PaymentRepository paymentRepository;
    private final RazorpayService razorpayService;
    private final PaymentEventProducer paymentEventProducer;
    private final UserClient userClient;

    @Override
    public PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request) throws RazorpayException {
        paymentRepository.findByBookingId(request.getBookingId())
                .ifPresent(payment -> {
                    if (payment.getStatus() == PaymentStatus.SUCCESS) {
                        throw new RuntimeException("payment already completed for this booking");
                    }
                });

        Payment payment = Payment.builder()
                .userId(request.getUserId())
                .bookingId(request.getBookingId())
                .amount(request.getAmount())
                .provider(request.getGateway())
                .status(PaymentStatus.PENDING)
                .transactionId(generateTransactionId())
                .build();

        payment = paymentRepository.save(payment);

        PaymentInitiateResponse response = PaymentInitiateResponse.builder()
                .paymentId(payment.getId())
                .gateway(request.getGateway())
                .transactionId(payment.getTransactionId())
                .amount(request.getAmount())
                .description(request.getDescription())
                .success(true)
                .message("payment initiated successfully")
                .build();

        if (request.getGateway() == PaymentGateway.RAZORPAY) {
//            fetch user details using feign client
            UserDTO userDTO = userClient.getUserById(request.getUserId());

//          create razorpay payment link using razorpay service
            PaymentLinkResponse paymentLinkResponse = razorpayService.createPaymentLink(
                    userDTO, payment
            );
            // set payment link to payment initiate response
            response.setRazorpayOrderId(paymentLinkResponse.getPayment_link_id());
            response.setCheckoutUrl(paymentLinkResponse.getPayment_link_url());


        }
        return response;
    }

    private String generateTransactionId() {
        return "TXN_" + System.currentTimeMillis() + "_" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    @Override
    public PaymentDTO verifyPayment(PaymentVerifyRequest request) throws Exception {

        JSONObject paymentDetails = razorpayService.fetchPaymentDetails(
                request.getRazorpayPaymentId()
        );

        String status = paymentDetails.optString("status");

        JSONObject notes = paymentDetails.getJSONObject("notes");
        Long paymentId = Long.parseLong(notes.optString("payment_id"));

        Payment payment = paymentRepository.findById(paymentId).orElseThrow(
                () -> new Exception("payment not found")
        );

        boolean isValid = "captured".equalsIgnoreCase(status);
        if (isValid) {
            if (payment.getProvider() == PaymentGateway.RAZORPAY) {
                payment.setProviderPaymentId(request.getRazorpayPaymentId());
            }
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            paymentRepository.save(payment);


//          publish payment success kafka event

            paymentEventProducer.sendPaymentCompleted(payment);

        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment verification failed");
            paymentRepository.save(payment);

//          publish payment failed kafka event
            paymentEventProducer.sendPaymentFailed(payment);
        }
        return PaymentMapper.toDTO(payment);
    }

    @Override
    public Page<PaymentDTO> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(
                PaymentMapper::toDTO
        );
    }

    @Override
    public Map<Long, PaymentDTO> getPaymentsByBookingIds(List<Long> bookingIds) {
        return paymentRepository.findByBookingIdIn(bookingIds)
                .stream().collect(Collectors.toMap(Payment::getId, PaymentMapper::toDTO));
    }
}
