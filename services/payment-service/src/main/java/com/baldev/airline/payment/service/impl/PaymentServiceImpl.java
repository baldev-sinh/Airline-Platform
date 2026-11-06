package com.baldev.airline.payment.service.impl;

import com.baldev.airline.common.enums.PaymentGateway;
import com.baldev.airline.common.enums.PaymentStatus;
import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.exception.UnauthorizedException;
import com.baldev.airline.common.payload.dto.PaymentDTO;
import com.baldev.airline.common.payload.dto.UserDTO;
import com.baldev.airline.common.payload.request.PaymentInitiateRequest;
import com.baldev.airline.common.payload.request.PaymentVerifyRequest;
import com.baldev.airline.common.payload.response.PaymentInitiateResponse;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.payment.client.UserClient;
import com.baldev.airline.payment.config.PaymentProperties;
import com.baldev.airline.payment.mapper.PaymentMapper;
import com.baldev.airline.payment.model.Payment;
import com.baldev.airline.payment.repository.PaymentRepository;
import com.baldev.airline.payment.service.PaymentService;
import com.baldev.airline.payment.service.gateway.PaymentProvider;
import com.baldev.airline.payment.service.gateway.PaymentProvider.CheckoutLink;
import com.baldev.airline.payment.service.gateway.PaymentProvider.GatewayPayment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Payment lifecycle. Remote gateway calls are made outside database transactions so a slow provider never
 * holds a DB connection; state changes are short transactions that row-lock the payment.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentProvider paymentProvider;
    private final UserClient userClient;
    private final PaymentProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Override
    public PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request) {
        if (request.getGateway() != PaymentGateway.RAZORPAY) {
            throw new BusinessRuleException("UNSUPPORTED_GATEWAY", "Payment gateway " + request.getGateway() + " is not supported");
        }
        if (paymentRepository.existsByBookingIdAndStatus(request.getBookingId(), PaymentStatus.SUCCESS)) {
            throw new ConflictException("ALREADY_PAID", "Booking " + request.getBookingId() + " is already paid");
        }

        // Idempotency: a retried checkout for the same booking and amount reuses the open payment link.
        Optional<Payment> open = paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(request.getBookingId(), PaymentStatus.PENDING)
                .filter(p -> p.getCheckoutUrl() != null
                        && p.getAmount().compareTo(Money.normalize(request.getAmount())) == 0);
        if (open.isPresent()) {
            log.info("Reusing pending paymentId={} for bookingId={}", open.get().getId(), request.getBookingId());
            return toInitiateResponse(open.get(), request.getDescription());
        }

        Payment payment = transactionTemplate.execute(status -> paymentRepository.save(Payment.builder()
                .userId(request.getUserId())
                .bookingId(request.getBookingId())
                .amount(Money.normalize(request.getAmount()))
                .currency(request.getCurrency() != null ? request.getCurrency() : properties.currency())
                .provider(request.getGateway())
                .status(PaymentStatus.PENDING)
                .transactionId(newTransactionId())
                .build()));

        CheckoutLink link;
        try {
            UserDTO user = userClient.getUserById(request.getUserId());
            link = paymentProvider.createPaymentLink(payment, user);
        } catch (RuntimeException e) {
            transactionTemplate.executeWithoutResult(status -> paymentRepository.findById(payment.getId())
                    .ifPresent(p -> {
                        p.setStatus(PaymentStatus.FAILED);
                        p.setFailureReason("Checkout link creation failed");
                    }));
            throw e;
        }

        Payment saved = transactionTemplate.execute(status -> {
            Payment p = paymentRepository.findById(payment.getId()).orElseThrow();
            p.setProviderOrderId(link.id());
            p.setCheckoutUrl(link.url());
            return p;
        });
        log.info("Initiated paymentId={} bookingId={} amount={} {}", saved.getId(), saved.getBookingId(),
                saved.getAmount(), saved.getCurrency());
        return toInitiateResponse(saved, request.getDescription());
    }

    @Override
    public PaymentDTO verifyPayment(CurrentUser user, PaymentVerifyRequest request) {
        GatewayPayment gatewayPayment = paymentProvider.fetchPayment(request.getRazorpayPaymentId());
        Long paymentId = parseId(gatewayPayment.notesPaymentId());

        return transactionTemplate.execute(status -> {
            Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                    .filter(p -> p.getUserId().equals(user.id()))
                    // Same response for "not found" and "not yours": never confirm another user's payment exists.
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
            settle(payment, gatewayPayment);
            return PaymentMapper.toDTO(payment);
        });
    }

    @Override
    public void handleRazorpayWebhook(String rawBody, String signature) {
        if (!paymentProvider.isValidWebhookSignature(rawBody, signature)) {
            log.warn("Rejected Razorpay webhook with invalid signature");
            throw new UnauthorizedException("Invalid webhook signature");
        }
        JSONObject json;
        String event;
        try {
            json = new JSONObject(rawBody);
            event = json.getString("event");
        } catch (JSONException e) {
            throw new BusinessRuleException("INVALID_WEBHOOK", "Malformed webhook payload");
        }
        JSONObject payload = json.optJSONObject("payload");
        String linkId = Optional.ofNullable(payload)
                .map(p -> p.optJSONObject("payment_link"))
                .map(p -> p.optJSONObject("entity"))
                .map(e -> e.optString("id", null))
                .orElse(null);
        if (linkId == null) {
            log.debug("Ignoring Razorpay webhook event={} without payment link", event);
            return;
        }

        transactionTemplate.executeWithoutResult(status -> {
            Optional<Payment> found = paymentRepository.findByProviderOrderIdForUpdate(linkId);
            if (found.isEmpty()) {
                log.warn("Razorpay webhook event={} for unknown payment link {}", event, linkId);
                return;
            }
            Payment payment = found.get();
            switch (event) {
                case "payment_link.paid" -> {
                    JSONObject entity = Optional.ofNullable(payload.optJSONObject("payment"))
                            .map(p -> p.optJSONObject("entity"))
                            .orElse(null);
                    if (entity == null) {
                        log.warn("payment_link.paid webhook for link {} has no payment entity", linkId);
                        return;
                    }
                    settle(payment, new GatewayPayment(entity.optString("id"), entity.optString("status"),
                            entity.optLong("amount"), entity.optString("currency"), null));
                }
                case "payment_link.expired", "payment_link.cancelled" -> {
                    if (payment.getStatus() == PaymentStatus.PENDING) {
                        fail(payment, "Checkout " + event.substring(event.indexOf('.') + 1));
                    }
                }
                default -> log.debug("Ignoring Razorpay webhook event={}", event);
            }
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentDTO getPayment(CurrentUser user, Long paymentId) {
        return paymentRepository.findById(paymentId)
                .filter(p -> user.isSystemAdmin() || p.getUserId().equals(user.id()))
                .map(PaymentMapper::toDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentDTO> getAllPayments(Pageable pageable) {
        return paymentRepository.findAll(pageable).map(PaymentMapper::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, PaymentDTO> getPaymentsByBookingIds(List<Long> bookingIds) {
        BinaryOperator<Payment> latest = (a, b) -> a.getId() > b.getId() ? a : b;
        return paymentRepository.findByBookingIdIn(bookingIds).stream()
                .collect(Collectors.toMap(Payment::getBookingId, Function.identity(), latest))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> PaymentMapper.toDTO(e.getValue())));
    }

    /**
     * Applies the gateway's verdict to a row-locked payment. Idempotent: an already-settled payment is
     * returned unchanged and no event is published again.
     */
    private void settle(Payment payment, GatewayPayment gatewayPayment) {
        if (payment.isTerminal()) {
            return;
        }
        if (!gatewayPayment.isCaptured()) {
            if ("failed".equalsIgnoreCase(gatewayPayment.status())) {
                fail(payment, "Payment failed at provider");
            }
            // authorized/created: not final yet, the webhook will settle it later.
            return;
        }
        boolean amountMatches = gatewayPayment.amountMinor() == Money.toMinorUnits(payment.getAmount());
        boolean currencyMatches = payment.getCurrency().equalsIgnoreCase(gatewayPayment.currency());
        if (!amountMatches || !currencyMatches) {
            log.error("SECURITY: amount/currency mismatch for paymentId={} expected={} {} got={} {} (minor units)",
                    payment.getId(), Money.toMinorUnits(payment.getAmount()), payment.getCurrency(),
                    gatewayPayment.amountMinor(), gatewayPayment.currency());
            fail(payment, "Paid amount does not match the booking amount");
            return;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setProviderPaymentId(gatewayPayment.id());
        payment.setPaidAt(clock.instant());
        paymentRepository.saveAndFlush(payment);

        // Delivered to Kafka after commit (PaymentEventProducer).
        eventPublisher.publishEvent(PaymentCompletedEvent.builder()
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .gateway(payment.getProvider().name())
                .transactionId(payment.getTransactionId())
                .providerPaymentId(payment.getProviderPaymentId())
                .paidAt(payment.getPaidAt())
                .build());
        log.info("Payment settled paymentId={} bookingId={}", payment.getId(), payment.getBookingId());
    }

    private void fail(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        eventPublisher.publishEvent(PaymentFailedEvent.builder()
                .paymentId(payment.getId())
                .bookingId(payment.getBookingId())
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .transactionId(payment.getTransactionId())
                .failureReason(reason)
                .failedAt(clock.instant())
                .build());
        log.warn("Payment failed paymentId={} bookingId={} reason={}", payment.getId(), payment.getBookingId(), reason);
    }

    private static Long parseId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException | NullPointerException e) {
            throw new BusinessRuleException("PAYMENT_NOT_LINKED", "This payment is not linked to a booking");
        }
    }

    private static String newTransactionId() {
        return "TXN_" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private static PaymentInitiateResponse toInitiateResponse(Payment payment, String description) {
        return PaymentInitiateResponse.builder()
                .paymentId(payment.getId())
                .gateway(payment.getProvider())
                .transactionId(payment.getTransactionId())
                .razorpayOrderId(payment.getProviderOrderId())
                .checkoutUrl(payment.getCheckoutUrl())
                .amount(payment.getAmount())
                .description(description)
                .success(true)
                .message("payment initiated successfully")
                .build();
    }
}
