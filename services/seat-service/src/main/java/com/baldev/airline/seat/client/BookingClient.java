package com.baldev.airline.seat.client;


import com.baldev.airline.common.payload.response.BookingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "booking-service")
public interface BookingClient {

    @GetMapping("/api/bookings/{id}")
    BookingResponse getBookingById(
            @PathVariable Long id);
}
