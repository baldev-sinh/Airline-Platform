package com.baldev.airline.booking.client;

import com.baldev.airline.common.payload.response.FlightInstanceResponse;
import com.baldev.airline.common.payload.response.FlightResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient("flight-ops-service")
public interface FlightClient {

    @GetMapping("/api/flights/{id}")
    FlightResponse getFlightById(@PathVariable Long id);

    @GetMapping("/api/flight-instances/{id}")
    FlightInstanceResponse getFlightInstanceById(
            @PathVariable Long id);
}
