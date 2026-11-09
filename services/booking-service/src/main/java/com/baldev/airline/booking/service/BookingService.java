package com.baldev.airline.booking.service;

import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.common.event.PaymentRefundedEvent;
import com.baldev.airline.common.event.RefundFailedEvent;
import com.baldev.airline.booking.payload.request.BookingRequest;
import com.baldev.airline.booking.payload.response.BookingResponse;
import com.baldev.airline.booking.client.dto.PaymentInitiateResponse;
import com.baldev.airline.common.web.CurrentUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface BookingService {

    /** Creates a PENDING booking, holds seats, prices it server-side and opens a payment checkout. */
    PaymentInitiateResponse createBooking(CurrentUser user, BookingRequest request);

    BookingResponse getBooking(CurrentUser user, Long id);

    Page<BookingResponse> getBookingsByAirline(CurrentUser user, String search, BookingStatus status,
                                               Long flightInstanceId, Pageable pageable);

    List<BookingResponse> getBookingsByUser(Long userId);

    /** Cancels a pending booking, or a ticketed one with a refund under the fare's rules. */
    BookingResponse cancelBooking(CurrentUser user, Long id);

    void deleteBooking(CurrentUser user, Long id);

    /** Idempotent: confirms the booking and issues tickets once. */
    void onPaymentCompleted(PaymentCompletedEvent event);

    /** Idempotent: cancels a still-pending booking and releases its seats. */
    void onPaymentFailed(PaymentFailedEvent event);

    /** Idempotent: marks the booking and its tickets refunded and tells the customer (booking.refunded). */
    void onPaymentRefunded(PaymentRefundedEvent event);

    /** Marks the booking's refund as failed; support must resolve it. */
    void onRefundFailed(RefundFailedEvent event);
}
