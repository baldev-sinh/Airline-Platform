package com.baldev.airline.seat.service.impl;

import com.baldev.airline.common.enums.SeatAvailabilityStatus;
import com.baldev.airline.common.enums.SeatType;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.common.payload.request.SeatHoldRequest;
import com.baldev.airline.common.payload.response.SeatHoldResponse;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.seat.config.SeatProperties;
import com.baldev.airline.seat.model.CabinClass;
import com.baldev.airline.seat.model.FlightInstanceCabin;
import com.baldev.airline.seat.model.Seat;
import com.baldev.airline.seat.model.SeatInstance;
import com.baldev.airline.seat.repository.CabinClassRepository;
import com.baldev.airline.seat.repository.FlightInstanceCabinRepository;
import com.baldev.airline.seat.repository.SeatInstanceRepository;
import com.baldev.airline.seat.repository.SeatRepository;
import com.baldev.airline.seat.service.SeatInventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeatInventoryServiceImpl implements SeatInventoryService {

    private static final int EXPIRY_BATCH_SIZE = 500;

    private final CabinClassRepository cabinClassRepository;
    private final SeatRepository seatRepository;
    private final FlightInstanceCabinRepository flightInstanceCabinRepository;
    private final SeatInstanceRepository seatInstanceRepository;
    private final SeatProperties properties;
    private final Clock clock;

    @Override
    @Transactional
    public void createInventory(Long flightId, Long flightInstanceId, Long aircraftId) {
        if (seatInstanceRepository.existsByFlightInstanceId(flightInstanceId)) {
            log.info("Seat inventory already exists for flightInstanceId={}, skipping", flightInstanceId);
            return;
        }
        List<CabinClass> cabinClasses = cabinClassRepository.findByAircraftId(aircraftId);
        if (cabinClasses.isEmpty()) {
            // Not retryable: the aircraft has no cabin configuration yet. Goes to the DLT for operator follow-up.
            throw new BusinessRuleException("NO_CABIN_CONFIGURATION",
                    "Aircraft " + aircraftId + " has no cabin classes; cannot build seat inventory");
        }
        int total = 0;
        for (CabinClass cabinClass : cabinClasses) {
            List<Seat> seats = cabinClass.getSeatMap() != null
                    ? seatRepository.findBySeatMapId(cabinClass.getSeatMap().getId())
                    : List.of();
            FlightInstanceCabin cabin = flightInstanceCabinRepository.save(FlightInstanceCabin.builder()
                    .flightInstanceId(flightInstanceId)
                    .cabinClass(cabinClass)
                    .totalSeats(seats.size())
                    .bookedSeats(0)
                    .build());
            List<SeatInstance> instances = seats.stream()
                    .map(seat -> SeatInstance.builder()
                            .flightId(flightId)
                            .flightInstanceId(flightInstanceId)
                            .flightInstanceCabin(cabin)
                            .seat(seat)
                            .status(SeatAvailabilityStatus.AVAILABLE)
                            .premiumSupercharge(surchargeFor(seat))
                            .build())
                    .toList();
            seatInstanceRepository.saveAll(instances);
            total += instances.size();
        }
        log.info("Created {} seat instances for flightInstanceId={}", total, flightInstanceId);
    }

    @Override
    @Transactional
    public SeatHoldResponse holdSeats(SeatHoldRequest request) {
        Set<Long> requested = new LinkedHashSet<>(request.getSeatInstanceIds());
        if (requested.size() != request.getSeatInstanceIds().size()) {
            throw new BusinessRuleException("DUPLICATE_SEAT", "The same seat was requested more than once");
        }

        Map<Long, SeatInstance> locked = seatInstanceRepository.findAllByIdForUpdate(requested).stream()
                .collect(Collectors.toMap(SeatInstance::getId, Function.identity()));

        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.holdDuration());
        BigDecimal total = BigDecimal.ZERO;

        // Validate everything first so the hold is all-or-nothing.
        for (Long id : requested) {
            SeatInstance seat = locked.get(id);
            if (seat == null || !Objects.equals(seat.getFlightInstanceId(), request.getFlightInstanceId())) {
                throw new ResourceNotFoundException("Seat " + id + " does not exist on this flight");
            }
            boolean heldBySameBooking = seat.getStatus() == SeatAvailabilityStatus.HELD
                    && Objects.equals(seat.getHeldByBookingId(), request.getBookingId());
            boolean expiredHold = seat.getStatus() == SeatAvailabilityStatus.HELD
                    && seat.getHoldExpiresAt() != null && seat.getHoldExpiresAt().isBefore(now);
            if (!seat.isAvailable() && !heldBySameBooking && !expiredHold) {
                throw new ConflictException("SEAT_UNAVAILABLE",
                        "Seat " + seatLabel(seat) + " is no longer available");
            }
        }
        for (Long id : requested) {
            SeatInstance seat = locked.get(id);
            seat.hold(request.getBookingId(), expiresAt);
            total = total.add(Money.orZero(seat.getPremiumSupercharge()));
        }
        log.info("Held {} seats for bookingId={} until {}", requested.size(), request.getBookingId(), expiresAt);
        return SeatHoldResponse.builder()
                .bookingId(request.getBookingId())
                .seatInstanceIds(List.copyOf(requested))
                .totalPrice(Money.normalize(total))
                .expiresAt(expiresAt)
                .build();
    }

    @Override
    @Transactional
    public void confirmSeats(Long bookingId) {
        List<SeatInstance> seats = seatInstanceRepository.findByBookingForUpdate(bookingId);
        int newlyBooked = 0;
        for (SeatInstance seat : seats) {
            if (seat.getStatus() == SeatAvailabilityStatus.HELD) {
                seat.book();
                incrementBooked(seat.getFlightInstanceCabin(), 1);
                newlyBooked++;
            }
        }
        if (seats.isEmpty()) {
            // The hold expired and the seats were released (and possibly resold) before payment completed.
            // Needs manual re-accommodation; surfaced via the DLT rather than silently overbooking.
            throw new ConflictException("SEAT_HOLD_LOST",
                    "No held seats found for booking " + bookingId + "; hold may have expired before payment");
        }
        log.info("Confirmed {} seats for bookingId={}", newlyBooked, bookingId);
    }

    @Override
    @Transactional
    public void releaseSeats(Long bookingId, boolean wasConfirmed) {
        List<SeatInstance> seats = seatInstanceRepository.findByBookingForUpdate(bookingId);
        for (SeatInstance seat : seats) {
            if (seat.isBooked()) {
                incrementBooked(seat.getFlightInstanceCabin(), -1);
            }
            seat.release();
        }
        log.info("Released {} seats for bookingId={} (wasConfirmed={})", seats.size(), bookingId, wasConfirmed);
    }

    @Override
    @Transactional
    public int releaseExpiredHolds() {
        List<SeatInstance> expired = seatInstanceRepository.findExpiredHoldsForUpdate(
                SeatAvailabilityStatus.HELD, clock.instant(), Limit.of(EXPIRY_BATCH_SIZE));
        expired.forEach(SeatInstance::release);
        if (!expired.isEmpty()) {
            log.info("Released {} expired seat holds", expired.size());
        }
        return expired.size();
    }

    private static void incrementBooked(FlightInstanceCabin cabin, int delta) {
        if (cabin != null) {
            int booked = cabin.getBookedSeats() != null ? cabin.getBookedSeats() : 0;
            cabin.setBookedSeats(Math.max(0, booked + delta));
        }
    }

    private BigDecimal surchargeFor(Seat seat) {
        if (seat.getPremiumSuperCharge() != null) {
            return Money.normalize(seat.getPremiumSuperCharge());
        }
        SeatType type = seat.getSeatType();
        if (type == SeatType.WINDOW) {
            return Money.normalize(properties.windowSurcharge());
        }
        if (type == SeatType.AISLE) {
            return Money.normalize(properties.aisleSurcharge());
        }
        return Money.normalize(BigDecimal.ZERO);
    }

    private static String seatLabel(SeatInstance seat) {
        return seat.getSeat() != null ? seat.getSeat().getSeatNumber() : String.valueOf(seat.getId());
    }
}
