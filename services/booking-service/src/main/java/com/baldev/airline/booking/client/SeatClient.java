package com.baldev.airline.booking.client;

import com.baldev.airline.common.payload.request.SeatHoldRequest;
import com.baldev.airline.common.payload.response.SeatHoldResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "seat-service", path = "/internal/seat-holds")
public interface SeatClient {

    @PostMapping
    SeatHoldResponse holdSeats(@RequestBody SeatHoldRequest request);

    @DeleteMapping("/{bookingId}")
    void releaseSeats(@PathVariable Long bookingId);
}
