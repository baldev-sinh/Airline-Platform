package com.baldev.airline.booking.service.impl;

import com.baldev.airline.booking.client.AirlineClient;
import com.baldev.airline.booking.client.AncillaryClient;
import com.baldev.airline.booking.client.FlightClient;
import com.baldev.airline.booking.client.PaymentClient;
import com.baldev.airline.booking.client.PricingClient;
import com.baldev.airline.booking.client.SeatClient;
import com.baldev.airline.booking.mapper.BookingMapper;
import com.baldev.airline.booking.mapper.PassengerMapper;
import com.baldev.airline.booking.model.Booking;
import com.baldev.airline.booking.model.Passenger;
import com.baldev.airline.booking.model.Ticket;
import com.baldev.airline.booking.repository.BookingRepository;
import com.baldev.airline.booking.service.BookingService;
import com.baldev.airline.booking.service.pricing.BookingPriceCalculator;
import com.baldev.airline.booking.service.pricing.BookingPriceCalculator.PriceBreakdown;
import com.baldev.airline.booking.service.pricing.RefundPolicy;
import com.baldev.airline.booking.service.pricing.RefundPolicy.RefundQuote;
import com.baldev.airline.booking.service.support.ReferenceGenerator;
import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.common.enums.FlightStatus;
import com.baldev.airline.common.enums.PaymentGateway;
import com.baldev.airline.common.enums.RefundStatus;
import com.baldev.airline.common.enums.TicketStatus;
import com.baldev.airline.common.enums.UserRole;
import com.baldev.airline.common.event.BookingCancelledEvent;
import com.baldev.airline.common.event.BookingConfirmedEvent;
import com.baldev.airline.common.event.BookingRefundedEvent;
import com.baldev.airline.common.event.PassengerNotificationData;
import com.baldev.airline.common.event.PaymentCompletedEvent;
import com.baldev.airline.common.event.PaymentFailedEvent;
import com.baldev.airline.common.event.PaymentRefundedEvent;
import com.baldev.airline.common.event.RefundFailedEvent;
import com.baldev.airline.common.event.RefundRequestedEvent;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ForbiddenException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.booking.payload.request.BookingRequest;
import com.baldev.airline.booking.payload.request.PassengerRequest;
import com.baldev.airline.booking.client.dto.PaymentInitiateRequest;
import com.baldev.airline.booking.client.dto.PriceQuoteRequest;
import com.baldev.airline.booking.client.dto.SeatHoldRequest;
import com.baldev.airline.booking.client.dto.AirportResponse;
import com.baldev.airline.booking.payload.response.BookingResponse;
import com.baldev.airline.booking.client.dto.FareResponse;
import com.baldev.airline.booking.client.dto.FareRulesResponse;
import com.baldev.airline.booking.client.dto.FlightInstanceResponse;
import com.baldev.airline.booking.client.dto.PaymentInitiateResponse;
import com.baldev.airline.booking.client.dto.SeatHoldResponse;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.common.web.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Booking orchestration (a small saga). Remote calls never run inside a DB transaction, and every step after
 * the seat hold has a compensating action, so a failure mid-checkout never leaves held seats or an orphaned
 * pending booking behind.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private static final Set<FlightStatus> BOOKABLE_STATUSES = Set.of(FlightStatus.SCHEDULED, FlightStatus.DELAYED);

    private final BookingRepository bookingRepository;
    private final FlightClient flightClient;
    private final PricingClient pricingClient;
    private final SeatClient seatClient;
    private final AncillaryClient ancillaryClient;
    private final PaymentClient paymentClient;
    private final AirlineClient airlineClient;
    private final ReferenceGenerator referenceGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Value("${airline.booking.max-passengers:9}")
    private int maxPassengers;

    @Value("${airline.payment.currency:INR}")
    private String currency;

    @Override
    public PaymentInitiateResponse createBooking(CurrentUser user, BookingRequest request) {
        validateRequest(request);

        FlightInstanceResponse flight = flightClient.getFlightInstanceById(request.getFlightInstanceId());
        validateFlight(request, flight);
        FareResponse fare = pricingClient.getFareById(request.getFareId());
        validateFare(request, fare);

        Booking booking = transactionTemplate.execute(status -> bookingRepository.save(
                newPendingBooking(user, request, flight, fare)));
        Long bookingId = booking.getId();
        log.info("Created pending booking id={} ref={} user={}", bookingId, booking.getBookingReference(), user.id());

        SeatHoldResponse hold;
        try {
            hold = seatClient.holdSeats(SeatHoldRequest.builder()
                    .bookingId(bookingId)
                    .flightInstanceId(request.getFlightInstanceId())
                    .seatInstanceIds(booking.getSeatInstanceIds())
                    .build());
        } catch (RuntimeException e) {
            cancelPending(bookingId, "Seat hold failed");
            throw e;
        }

        try {
            BigDecimal ancillaries = ancillaryClient.quoteAncillaries(PriceQuoteRequest.builder()
                    .flightId(request.getFlightId())
                    .cabinClassId(fare.getCabinClassId())
                    .itemIds(nullSafe(request.getAncillaryIds()))
                    .build()).getTotal();
            BigDecimal meals = ancillaryClient.quoteMeals(PriceQuoteRequest.builder()
                    .flightId(request.getFlightId())
                    .itemIds(nullSafe(request.getMealIds()))
                    .build()).getTotal();

            PriceBreakdown price = BookingPriceCalculator.calculate(
                    fare.getTotalPrice(), request.getPassengers().size(), hold.getTotalPrice(), ancillaries, meals);

            transactionTemplate.executeWithoutResult(status -> {
                Booking b = bookingRepository.findById(bookingId).orElseThrow();
                b.setFareAmount(price.fareAmount());
                b.setSeatAmount(price.seatAmount());
                b.setAncillaryAmount(price.ancillaryAmount());
                b.setMealAmount(price.mealAmount());
                b.setTotalAmount(price.totalAmount());
                b.setTicketTimeLimit(LocalDateTime.ofInstant(hold.getExpiresAt(), ZoneOffset.UTC));
            });

            PaymentInitiateResponse payment = paymentClient.initiatePayment(PaymentInitiateRequest.builder()
                    .userId(user.id())
                    .bookingId(bookingId)
                    .amount(price.totalAmount())
                    .currency(currency)
                    .gateway(PaymentGateway.RAZORPAY)
                    .description("Booking " + booking.getBookingReference())
                    .build());

            transactionTemplate.executeWithoutResult(status ->
                    bookingRepository.findById(bookingId).orElseThrow().setPaymentId(payment.getPaymentId()));
            return payment;
        } catch (RuntimeException e) {
            releaseSeatsQuietly(bookingId);
            cancelPending(bookingId, "Checkout failed");
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BookingResponse getBooking(CurrentUser user, Long id) {
        Booking booking = bookingRepository.findWithDetailsById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
        if (!canView(user, booking)) {
            // 404 rather than 403: do not reveal that someone else's booking id exists.
            throw ResourceNotFoundException.of("Booking", id);
        }
        return BookingMapper.toResponse(booking);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BookingResponse> getBookingsByAirline(CurrentUser user, String search, BookingStatus status,
                                                      Long flightInstanceId, Pageable pageable) {
        Long airlineId = airlineClient.getAirlineByOwner(user.id()).getId();
        String pattern = (search == null || search.isBlank())
                ? null
                : "%" + search.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return bookingRepository.searchByAirline(airlineId, pattern, status, flightInstanceId, pageable)
                .map(BookingMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByUser(Long userId) {
        return bookingRepository.findByUserIdOrderByBookingDateDesc(userId).stream()
                .map(BookingMapper::toResponse)
                .toList();
    }

    /**
     * Cancels a booking. A pending one is simply cancelled. A ticketed one is cancelled under its fare's refund
     * rules: tickets are voided, the seats go back on sale (booking.cancelled) and the refundable amount is
     * requested from payment-service (refund.requested), all in one transaction through the outbox.
     * The fare rules are fetched before that transaction, so no remote call runs while the booking row is locked.
     */
    @Override
    public BookingResponse cancelBooking(CurrentUser user, Long id) {
        Booking current = transactionTemplate.execute(status -> bookingRepository.findById(id)
                .filter(b -> b.getUserId().equals(user.id()) || user.isSystemAdmin())
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", id)));
        FareRulesResponse rules = current.getStatus() == BookingStatus.CONFIRMED ? fareRulesOf(current.getFareId()) : null;

        return transactionTemplate.execute(status -> {
            Booking booking = bookingRepository.findByIdForUpdate(id)
                    .orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
            switch (booking.getStatus()) {
                case CANCELLED -> {
                    return BookingMapper.toResponse(booking);
                }
                case PENDING -> markCancelled(booking, "Cancelled by customer", false);
                case CONFIRMED -> cancelTicketed(booking, rules);
                default -> throw new BusinessRuleException("NOT_CANCELLABLE",
                        "A booking in status " + booking.getStatus() + " cannot be cancelled");
            }
            return BookingMapper.toResponse(booking);
        });
    }

    @Override
    @Transactional
    public void deleteBooking(CurrentUser user, Long id) {
        if (!user.isSystemAdmin()) {
            throw new ForbiddenException("Only system administrators can purge bookings");
        }
        Booking booking = bookingRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Booking", id));
        if (booking.getStatus() != BookingStatus.CANCELLED) {
            throw new BusinessRuleException("NOT_DELETABLE", "Only cancelled bookings can be purged");
        }
        bookingRepository.delete(booking);
    }

    @Override
    @Transactional
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        Booking booking = bookingRepository.findByIdForUpdate(event.getBookingId()).orElse(null);
        if (booking == null) {
            throw ResourceNotFoundException.of("Booking", event.getBookingId());
        }
        if (booking.isTicketIssued() && Objects.equals(booking.getPaymentId(), event.getPaymentId())) {
            log.info("Duplicate PaymentCompleted for bookingId={}, already ticketed", booking.getId());
            return;
        }
        String refundKey = "booking-" + booking.getId() + "-payment-" + event.getPaymentId();
        if (booking.getStatus() != BookingStatus.PENDING) {
            // Paid after the booking was cancelled (e.g. the seat hold expired first): never confirm silently,
            // give the money back.
            log.warn("Payment {} captured for booking {} in status {}; refunding it",
                    event.getPaymentId(), booking.getId(), booking.getStatus());
            requestRefund(booking, event.getPaymentId(), event.getAmount(), refundKey,
                    "Payment received after the booking was " + booking.getStatus().name().toLowerCase());
            return;
        }
        if (booking.getTotalAmount() == null || event.getAmount() == null
                || booking.getTotalAmount().compareTo(event.getAmount()) != 0) {
            log.error("Paid amount {} does not match booking {} total {}; cancelling and refunding",
                    event.getAmount(), booking.getId(), booking.getTotalAmount());
            booking.setPaymentId(event.getPaymentId());
            markCancelled(booking, "Paid amount did not match the booking total", false);
            requestRefund(booking, event.getPaymentId(), event.getAmount(), refundKey, "Paid amount mismatch");
            return;
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setPaymentId(event.getPaymentId());
        issueTickets(booking);
        eventPublisher.publishEvent(toConfirmedEvent(booking, event));
        log.info("Booking confirmed id={} ref={}", booking.getId(), booking.getBookingReference());
    }

    @Override
    @Transactional
    public void onPaymentFailed(PaymentFailedEvent event) {
        bookingRepository.findByIdForUpdate(event.getBookingId())
                .filter(b -> b.getStatus() == BookingStatus.PENDING)
                .ifPresent(b -> markCancelled(b, "Payment failed: " + event.getFailureReason(), false));
    }

    @Override
    @Transactional
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        Booking booking = bookingRepository.findByIdForUpdate(event.getBookingId())
                .orElseThrow(() -> ResourceNotFoundException.of("Booking", event.getBookingId()));
        if (booking.getRefundStatus() == RefundStatus.REFUNDED
                && Objects.equals(booking.getRefundReference(), event.getProviderRefundId())) {
            log.info("Duplicate PaymentRefunded for bookingId={}", booking.getId());
            return;
        }
        booking.setRefundStatus(RefundStatus.REFUNDED);
        booking.setRefundAmount(event.getAmount());
        booking.setRefundReference(event.getProviderRefundId());
        booking.setRefundedAt(event.getRefundedAt() != null ? event.getRefundedAt() : clock.instant());
        booking.getTickets().forEach(t -> t.setStatus(TicketStatus.REFUNDED));
        eventPublisher.publishEvent(BookingRefundedEvent.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .userId(booking.getUserId())
                .contactEmail(booking.getContactInfo() != null ? booking.getContactInfo().getEmail() : null)
                .flightNumber(booking.getFlightNumber())
                .departureAirportCode(booking.getDepartureAirportCode())
                .arrivalAirportCode(booking.getArrivalAirportCode())
                .departureDateTime(booking.getDepartureDateTime())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .providerRefundId(event.getProviderRefundId())
                .refundedAt(booking.getRefundedAt())
                .build());
        log.info("Booking {} refunded {} {} ({})", booking.getId(), event.getAmount(), event.getCurrency(),
                event.getProviderRefundId());
    }

    @Override
    @Transactional
    public void onRefundFailed(RefundFailedEvent event) {
        bookingRepository.findByIdForUpdate(event.getBookingId()).ifPresent(booking -> {
            if (booking.getRefundStatus() == RefundStatus.REFUNDED) {
                return;
            }
            booking.setRefundStatus(RefundStatus.FAILED);
            log.error("ACTION REQUIRED: refund {} for booking {} failed at the provider: {}",
                    event.getRefundKey(), booking.getId(), event.getReason());
        });
    }

    // ------------------------------------------------------------------------------------------------------

    private void validateRequest(BookingRequest request) {
        List<PassengerRequest> passengers = request.getPassengers();
        if (passengers.size() > maxPassengers) {
            throw new BusinessRuleException("TOO_MANY_PASSENGERS", "At most " + maxPassengers + " passengers per booking");
        }
        Set<Long> seats = new HashSet<>();
        for (PassengerRequest p : passengers) {
            if (!seats.add(p.getSeatInstanceId())) {
                throw new BusinessRuleException("DUPLICATE_SEAT", "Each passenger needs a different seat");
            }
        }
    }

    private void validateFlight(BookingRequest request, FlightInstanceResponse flight) {
        if (!Objects.equals(flight.getFlightId(), request.getFlightId())) {
            throw new BusinessRuleException("FLIGHT_MISMATCH", "Flight instance does not belong to the selected flight");
        }
        if (!Boolean.TRUE.equals(flight.getIsActive()) || !BOOKABLE_STATUSES.contains(flight.getStatus())) {
            throw new BusinessRuleException("FLIGHT_NOT_BOOKABLE", "This flight is not open for booking");
        }
        if (flight.getDepartureDateTime() == null
                || !flight.getDepartureDateTime().isAfter(LocalDateTime.now(clock.withZone(ZoneOffset.UTC)))) {
            throw new BusinessRuleException("FLIGHT_DEPARTED", "This flight has already departed");
        }
    }

    private static void validateFare(BookingRequest request, FareResponse fare) {
        if (!Objects.equals(fare.getFlightId(), request.getFlightId())) {
            throw new BusinessRuleException("FARE_MISMATCH", "Fare does not belong to the selected flight");
        }
        if (fare.getCabinClass() != null && request.getCabinClass() != null
                && fare.getCabinClass() != request.getCabinClass()) {
            throw new BusinessRuleException("FARE_MISMATCH", "Fare does not belong to the selected cabin");
        }
    }

    private Booking newPendingBooking(CurrentUser user, BookingRequest request, FlightInstanceResponse flight,
                                      FareResponse fare) {
        AirportResponse dep = flight.getDepartureAirport();
        AirportResponse arr = flight.getArrivalAirport();
        Booking booking = Booking.builder()
                .bookingReference(referenceGenerator.newBookingReference())
                .userId(user.id())
                .flightId(request.getFlightId())
                .flightInstanceId(request.getFlightInstanceId())
                .airlineId(flight.getAirlineId())
                .fareId(request.getFareId())
                .cabinClass(request.getCabinClass())
                .contactInfo(request.getContactInfo())
                .ancillaryIds(new ArrayList<>(nullSafe(request.getAncillaryIds())))
                .mealIds(new ArrayList<>(nullSafe(request.getMealIds())))
                .seatInstanceIds(request.getPassengers().stream().map(PassengerRequest::getSeatInstanceId).toList())
                .flightNumber(flight.getFlightNumber())
                .airlineName(flight.getAirlineName())
                .departureAirportCode(dep != null ? dep.getIataCode() : null)
                .arrivalAirportCode(arr != null ? arr.getIataCode() : null)
                .departureCity(dep != null && dep.getCity() != null ? dep.getCity().getName() : null)
                .arrivalCity(arr != null && arr.getCity() != null ? arr.getCity().getName() : null)
                .departureDateTime(flight.getDepartureDateTime())
                .arrivalDateTime(flight.getArrivalDateTime())
                .fareName(fare.getName())
                .currency(currency)
                .status(BookingStatus.PENDING)
                .build();
        for (PassengerRequest p : request.getPassengers()) {
            Passenger passenger = PassengerMapper.toEntity(p);
            passenger.setPrimaryUserId(user.id());
            booking.addPassenger(passenger);
        }
        return booking;
    }

    private void issueTickets(Booking booking) {
        if (booking.isTicketIssued()) {
            return;
        }
        Instant now = clock.instant();
        for (Passenger passenger : booking.getPassengers()) {
            booking.addTicket(Ticket.builder()
                    .ticketNumber(referenceGenerator.newTicketNumber())
                    .status(TicketStatus.BOOKED)
                    .issuedAt(now)
                    .passenger(passenger)
                    .build());
        }
        booking.setTicketIssued(true);
    }

    private void cancelTicketed(Booking booking, FareRulesResponse rules) {
        RefundQuote quote = RefundPolicy.quote(booking.getTotalAmount(), booking.getPassengers().size(), rules,
                booking.getDepartureDateTime(), LocalDateTime.now(clock.withZone(ZoneOffset.UTC)));
        booking.getTickets().forEach(t -> t.setStatus(TicketStatus.CANCELLED));
        boolean refund = quote.isRefundable() && booking.getPaymentId() != null;
        if (refund) {
            booking.setRefundAmount(quote.amount());
        }
        markCancelled(booking, "Cancelled by customer: " + quote.reason(), true);
        if (refund) {
            requestRefund(booking, booking.getPaymentId(), quote.amount(), "booking-" + booking.getId() + "-cancel",
                    "Customer cancellation: " + quote.reason());
        }
    }

    /** Records the refund on the booking and asks payment-service for it (idempotent per refund key). */
    private void requestRefund(Booking booking, Long paymentId, BigDecimal amount, String refundKey, String reason) {
        if (amount == null || amount.signum() <= 0 || paymentId == null) {
            return;
        }
        booking.setRefundStatus(RefundStatus.PENDING);
        booking.setRefundAmount(Money.normalize(amount));
        eventPublisher.publishEvent(RefundRequestedEvent.builder()
                .refundKey(refundKey)
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .paymentId(paymentId)
                .userId(booking.getUserId())
                .amount(Money.normalize(amount))
                .currency(booking.getCurrency())
                .reason(reason)
                .requestedAt(clock.instant())
                .build());
        log.info("Refund {} requested for booking {}: {} {}", refundKey, booking.getId(), amount, booking.getCurrency());
    }

    private FareRulesResponse fareRulesOf(Long fareId) {
        try {
            return pricingClient.getFareRulesByFareId(fareId);
        } catch (ResourceNotFoundException e) {
            return null; // the fare has no rules: default policy
        }
    }

    private void markCancelled(Booking booking, String reason, boolean wasConfirmed) {
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(reason.length() > 255 ? reason.substring(0, 255) : reason);
        booking.setCancelledAt(clock.instant());
        eventPublisher.publishEvent(BookingCancelledEvent.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .flightInstanceId(booking.getFlightInstanceId())
                .seatInstanceIds(List.copyOf(booking.getSeatInstanceIds()))
                .wasConfirmed(wasConfirmed)
                .reason(reason)
                .cancelledAt(booking.getCancelledAt())
                .userId(booking.getUserId())
                .contactEmail(booking.getContactInfo() != null ? booking.getContactInfo().getEmail() : null)
                .flightNumber(booking.getFlightNumber())
                .departureAirportCode(booking.getDepartureAirportCode())
                .arrivalAirportCode(booking.getArrivalAirportCode())
                .departureDateTime(booking.getDepartureDateTime())
                .refundAmount(booking.getRefundAmount())
                .currency(booking.getCurrency())
                .build());
        log.info("Booking cancelled id={} reason={}", booking.getId(), reason);
    }

    private void cancelPending(Long bookingId, String reason) {
        try {
            transactionTemplate.executeWithoutResult(status -> bookingRepository.findByIdForUpdate(bookingId)
                    .filter(b -> b.getStatus() == BookingStatus.PENDING)
                    .ifPresent(b -> markCancelled(b, reason, false)));
        } catch (RuntimeException e) {
            log.error("Compensation failed: could not cancel pending booking {}", bookingId, e);
        }
    }

    private void releaseSeatsQuietly(Long bookingId) {
        try {
            seatClient.releaseSeats(bookingId);
        } catch (RuntimeException e) {
            // The hold expires on its own; the booking.cancelled event is a second release path.
            log.warn("Seat release for booking {} failed; relying on hold expiry", bookingId, e);
        }
    }

    private static boolean isAirlineStaff(CurrentUser user) {
        return user.hasRole(UserRole.ROLE_AIRLINE_OWNER);
    }

    private boolean canView(CurrentUser user, Booking booking) {
        if (booking.getUserId().equals(user.id()) || user.isSystemAdmin()) {
            return true;
        }
        return isAirlineStaff(user)
                && Objects.equals(airlineClient.getAirlineByOwner(user.id()).getId(), booking.getAirlineId());
    }

    private static List<Long> nullSafe(List<Long> ids) {
        return ids != null ? ids : List.of();
    }

    private static BookingConfirmedEvent toConfirmedEvent(Booking booking, PaymentCompletedEvent payment) {
        Map<Long, String> ticketByPassenger = booking.getTickets().stream()
                .collect(Collectors.toMap(t -> t.getPassenger().getId(), Ticket::getTicketNumber, (a, b) -> a));
        List<PassengerNotificationData> passengers = booking.getPassengers().stream()
                .map(p -> PassengerNotificationData.builder()
                        .firstName(p.getFirstName())
                        .lastName(p.getLastName())
                        .ticketNumber(ticketByPassenger.get(p.getId()))
                        .nationality(p.getNationality())
                        .gender(p.getGender() != null ? p.getGender().name() : null)
                        .adult(p.isAdult())
                        .build())
                .toList();
        return BookingConfirmedEvent.builder()
                .bookingId(booking.getId())
                .bookingReference(booking.getBookingReference())
                .confirmedAt(LocalDateTime.now(ZoneOffset.UTC))
                .bookingDate(booking.getBookingDate() != null
                        ? LocalDateTime.ofInstant(booking.getBookingDate(), ZoneOffset.UTC) : null)
                .cabinClass(booking.getCabinClass() != null ? booking.getCabinClass().name() : null)
                .flexibleTicket(booking.isFlexibleTicket())
                .userId(booking.getUserId())
                .contactEmail(booking.getContactInfo() != null ? booking.getContactInfo().getEmail() : null)
                .contactPhone(booking.getContactInfo() != null ? booking.getContactInfo().getPhone() : null)
                .passengers(passengers)
                .flightInstanceId(booking.getFlightInstanceId())
                .flightNumber(booking.getFlightNumber())
                .airlineName(booking.getAirlineName())
                .departureAirportCode(booking.getDepartureAirportCode())
                .departureCity(booking.getDepartureCity())
                .departureDateTime(booking.getDepartureDateTime())
                .arrivalAirportCode(booking.getArrivalAirportCode())
                .arrivalCity(booking.getArrivalCity())
                .arrivalDateTime(booking.getArrivalDateTime())
                .totalAmount(booking.getTotalAmount())
                .currency(booking.getCurrency())
                .transactionId(payment.getTransactionId())
                .providerPaymentId(payment.getProviderPaymentId())
                .paymentGateway(payment.getGateway())
                .paidAt(payment.getPaidAt() != null ? LocalDateTime.ofInstant(payment.getPaidAt(), ZoneOffset.UTC) : null)
                .fareName(booking.getFareName())
                .seatFees(Money.orZero(booking.getSeatAmount()))
                .ancillaryFees(Money.orZero(booking.getAncillaryAmount()))
                .mealFees(Money.orZero(booking.getMealAmount()))
                .seatInstanceIds(List.copyOf(booking.getSeatInstanceIds()))
                .build();
    }
}
