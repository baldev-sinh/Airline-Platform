package com.baldev.airline.seat.controller.internal;

import com.baldev.airline.seat.payload.request.SeatHoldRequest;
import com.baldev.airline.seat.payload.response.SeatHoldResponse;
import com.baldev.airline.seat.service.SeatInventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service seat hold API used by booking-service. Never routed by the API gateway. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/seat-holds")
public class InternalSeatHoldController {

    private final SeatInventoryService seatInventoryService;

    @PostMapping
    public SeatHoldResponse hold(@Valid @RequestBody SeatHoldRequest request) {
        return seatInventoryService.holdSeats(request);
    }

    /** Compensation when checkout fails after the hold succeeded. */
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<Void> release(@PathVariable Long bookingId) {
        seatInventoryService.releaseSeats(bookingId, false);
        return ResponseEntity.noContent().build();
    }
}
