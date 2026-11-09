package com.baldev.airline.payment.messaging;

import com.baldev.airline.common.event.RefundRequestedEvent;
import com.baldev.airline.common.kafka.KafkaTopics;
import com.baldev.airline.payment.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Refunds requested by booking-service. Idempotent per refund key, so redelivery is safe. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefundRequestListener {

    private final RefundService refundService;

    @KafkaListener(topics = KafkaTopics.REFUND_REQUESTED)
    public void onRefundRequested(RefundRequestedEvent event) {
        log.info("Received RefundRequested key={} bookingId={} amount={} {}", event.getRefundKey(),
                event.getBookingId(), event.getAmount(), event.getCurrency());
        refundService.requestRefund(event);
    }
}
