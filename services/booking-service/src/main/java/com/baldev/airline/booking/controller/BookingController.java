package com.baldev.airline.booking.controller;

import com.baldev.airline.booking.service.BookingService;
import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.booking.payload.request.BookingRequest;
import com.baldev.airline.booking.payload.response.BookingResponse;
import com.baldev.airline.booking.client.dto.PaymentInitiateResponse;
import com.baldev.airline.common.web.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    @PostMapping
    public ResponseEntity<PaymentInitiateResponse> createBooking(CurrentUser user,
                                                                 @Valid @RequestBody BookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(user, request));
    }

    @GetMapping("/{id}")
    public BookingResponse getBookingById(@PathVariable Long id, CurrentUser user) {
        return bookingService.getBooking(user, id);
    }

    @GetMapping("/airline")
    public Page<BookingResponse> getBookingsByAirline(
            CurrentUser user,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Long flightInstanceId,
            @PageableDefault(size = 20, sort = "bookingDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.getBookingsByAirline(user, search, status, flightInstanceId, pageable);
    }

    @GetMapping("/user/history")
    public List<BookingResponse> getBookingsByUser(CurrentUser user) {
        return bookingService.getBookingsByUser(user.id());
    }

    @PatchMapping("/{id}/cancel")
    public BookingResponse cancelBooking(@PathVariable Long id, CurrentUser user) {
        return bookingService.cancelBooking(user, id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBooking(@PathVariable Long id, CurrentUser user) {
        bookingService.deleteBooking(user, id);
        return ResponseEntity.noContent().build();
    }
}
