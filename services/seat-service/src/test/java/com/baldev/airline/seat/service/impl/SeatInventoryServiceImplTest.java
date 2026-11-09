package com.baldev.airline.seat.service.impl;

import com.baldev.airline.common.enums.SeatAvailabilityStatus;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.seat.payload.request.SeatHoldRequest;
import com.baldev.airline.seat.payload.response.SeatHoldResponse;
import com.baldev.airline.seat.config.SeatProperties;
import com.baldev.airline.seat.model.FlightInstanceCabin;
import com.baldev.airline.seat.model.SeatInstance;
import com.baldev.airline.seat.repository.CabinClassRepository;
import com.baldev.airline.seat.repository.FlightInstanceCabinRepository;
import com.baldev.airline.seat.repository.SeatInstanceRepository;
import com.baldev.airline.seat.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SeatInventoryServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");
    private static final long FLIGHT_INSTANCE = 100L;

    private final SeatInstanceRepository seatInstances = mock(SeatInstanceRepository.class);
    private final FlightInstanceCabinRepository cabins = mock(FlightInstanceCabinRepository.class);
    private SeatInventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SeatInventoryServiceImpl(
                mock(CabinClassRepository.class), mock(SeatRepository.class), cabins,
                seatInstances,
                new SeatProperties(Duration.ofMinutes(20), new BigDecimal("1000"), new BigDecimal("500")),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void holdsAllRequestedSeatsAndPricesThem() {
        SeatInstance a = seat(1L, SeatAvailabilityStatus.AVAILABLE, "1000.00");
        SeatInstance b = seat(2L, SeatAvailabilityStatus.AVAILABLE, "500.00");
        when(seatInstances.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(a, b));

        SeatHoldResponse response = service.holdSeats(request(9L, 1L, 2L));

        assertThat(response.getTotalPrice()).isEqualByComparingTo("1500.00");
        assertThat(response.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(20)));
        assertThat(List.of(a, b)).allSatisfy(s -> {
            assertThat(s.getStatus()).isEqualTo(SeatAvailabilityStatus.HELD);
            assertThat(s.getHeldByBookingId()).isEqualTo(9L);
        });
    }

    @Test
    void holdIsAllOrNothingWhenAnySeatIsTaken() {
        SeatInstance free = seat(1L, SeatAvailabilityStatus.AVAILABLE, "0");
        SeatInstance taken = seat(2L, SeatAvailabilityStatus.BOOKED, "0");
        when(seatInstances.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(free, taken));

        assertThatThrownBy(() -> service.holdSeats(request(9L, 1L, 2L))).isInstanceOf(ConflictException.class);
        assertThat(free.getStatus()).isEqualTo(SeatAvailabilityStatus.AVAILABLE);
    }

    @Test
    void cannotTakeSeatHeldByAnotherActiveBooking() {
        SeatInstance held = seat(1L, SeatAvailabilityStatus.HELD, "0");
        held.hold(55L, NOW.plusSeconds(60));
        when(seatInstances.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(held));

        assertThatThrownBy(() -> service.holdSeats(request(9L, 1L))).isInstanceOf(ConflictException.class);
    }

    @Test
    void expiredHoldOfAnotherBookingCanBeTaken() {
        SeatInstance stale = seat(1L, SeatAvailabilityStatus.HELD, "0");
        stale.hold(55L, NOW.minusSeconds(1));
        when(seatInstances.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(stale));

        service.holdSeats(request(9L, 1L));
        assertThat(stale.getHeldByBookingId()).isEqualTo(9L);
    }

    @Test
    void retryBySameBookingIsIdempotent() {
        SeatInstance mine = seat(1L, SeatAvailabilityStatus.HELD, "0");
        mine.hold(9L, NOW.plusSeconds(60));
        when(seatInstances.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(mine));

        service.holdSeats(request(9L, 1L));
        assertThat(mine.getStatus()).isEqualTo(SeatAvailabilityStatus.HELD);
    }

    @Test
    void rejectsSeatFromAnotherFlight() {
        SeatInstance other = seat(1L, SeatAvailabilityStatus.AVAILABLE, "0");
        other.setFlightInstanceId(999L);
        when(seatInstances.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(other));

        assertThatThrownBy(() -> service.holdSeats(request(9L, 1L))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void confirmBooksHeldSeatsOnceEvenIfRedelivered() {
        FlightInstanceCabin cabin = FlightInstanceCabin.builder().id(7L).totalSeats(10).build();
        SeatInstance mine = seat(1L, SeatAvailabilityStatus.HELD, "0");
        mine.hold(9L, NOW.plusSeconds(60));
        mine.setFlightInstanceCabin(cabin);
        when(seatInstances.findByBookingForUpdate(9L)).thenReturn(List.of(mine));

        service.confirmSeats(9L);
        service.confirmSeats(9L);

        assertThat(mine.getStatus()).isEqualTo(SeatAvailabilityStatus.BOOKED);
        verify(cabins, times(1)).adjustBookedSeats(7L, 1);
    }

    @Test
    void confirmCountsSeatsOncePerCabin() {
        FlightInstanceCabin economy = FlightInstanceCabin.builder().id(7L).totalSeats(10).build();
        FlightInstanceCabin business = FlightInstanceCabin.builder().id(3L).totalSeats(4).build();
        SeatInstance a = heldInCabin(1L, economy);
        SeatInstance b = heldInCabin(2L, economy);
        SeatInstance c = heldInCabin(3L, business);
        when(seatInstances.findByBookingForUpdate(9L)).thenReturn(List.of(a, b, c));

        service.confirmSeats(9L);

        verify(cabins).adjustBookedSeats(7L, 2);
        verify(cabins).adjustBookedSeats(3L, 1);
        verifyNoMoreInteractions(cabins);
    }

    @Test
    void releaseDecrementsOnlyForBookedSeats() {
        FlightInstanceCabin cabin = FlightInstanceCabin.builder().id(7L).totalSeats(10).build();
        SeatInstance booked = heldInCabin(1L, cabin);
        booked.book();
        SeatInstance held = heldInCabin(2L, cabin);
        when(seatInstances.findByBookingForUpdate(9L)).thenReturn(List.of(booked, held));

        service.releaseSeats(9L, true);

        assertThat(List.of(booked, held)).allSatisfy(s -> assertThat(s.isAvailable()).isTrue());
        verify(cabins).adjustBookedSeats(7L, -1);
        verifyNoMoreInteractions(cabins);
    }

    @Test
    void releaseOfUnknownBookingTouchesNothing() {
        when(seatInstances.findByBookingForUpdate(9L)).thenReturn(List.of());

        service.releaseSeats(9L, false);

        verifyNoInteractions(cabins);
    }

    @Test
    void confirmWithLostHoldIsSurfacedNotIgnored() {
        when(seatInstances.findByBookingForUpdate(9L)).thenReturn(List.of());
        assertThatThrownBy(() -> service.confirmSeats(9L)).isInstanceOf(ConflictException.class);
    }

    private static SeatInstance seat(Long id, SeatAvailabilityStatus status, String surcharge) {
        return SeatInstance.builder()
                .id(id)
                .flightId(1L)
                .flightInstanceId(FLIGHT_INSTANCE)
                .status(status)
                .premiumSupercharge(new BigDecimal(surcharge))
                .build();
    }

    private static SeatInstance heldInCabin(Long id, FlightInstanceCabin cabin) {
        SeatInstance seat = seat(id, SeatAvailabilityStatus.HELD, "0");
        seat.hold(9L, NOW.plusSeconds(60));
        seat.setFlightInstanceCabin(cabin);
        return seat;
    }

    private static SeatHoldRequest request(Long bookingId, Long... seatIds) {
        return SeatHoldRequest.builder()
                .bookingId(bookingId)
                .flightInstanceId(FLIGHT_INSTANCE)
                .seatInstanceIds(List.of(seatIds))
                .build();
    }
}
