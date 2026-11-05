package com.baldev.airline.booking.controller;

import com.baldev.airline.common.enums.BookingStatus;
import com.baldev.airline.common.payload.request.BookingRequest;
import com.baldev.airline.common.payload.response.ApiResponse;
import com.baldev.airline.common.payload.response.BookingResponse;
import com.baldev.airline.common.payload.response.PaymentInitiateResponse;
import com.baldev.airline.booking.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<PaymentInitiateResponse> createBooking(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody BookingRequest bookingRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        bookingService.createBooking(bookingRequest, userId)
                );
    }

    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable Long id) throws Exception {
        BookingResponse response = bookingService.getBookingById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/airline")
    public ResponseEntity<List<BookingResponse>> getBookingsByAirline(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Long flightInstanceId,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestHeader("X-User-Id") Long userId
    ) {
        List<BookingResponse> responses = bookingService.getAllBookingsByAirline(
                userId,
                search,
                status,
                flightInstanceId,
                sortDirection
        );
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/user/history")
    public ResponseEntity<List<BookingResponse>> getBookingsByUser(
            @RequestHeader("X-User-Id") Long userId) {
        List<BookingResponse> responses = bookingService.getBookingsByUser(userId);
        return ResponseEntity.ok(responses);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) throws Exception {
        BookingResponse response = bookingService.cancelBooking(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteBooking(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) throws Exception {
        bookingService.deleteBooking(id);
        ApiResponse response = new ApiResponse("Booking deleted successfully");
        return ResponseEntity.ok(response);
    }
}
