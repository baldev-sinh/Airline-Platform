package com.baldev.airline.seat.client;

import com.baldev.airline.seat.client.dto.FlightInstanceResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "flight-ops-service")
public interface FlightOpsClient {

    @GetMapping("/api/flight-instances/{id}")
    FlightInstanceResponse getFlightInstanceById(@PathVariable Long id);
}
