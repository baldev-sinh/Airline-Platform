package com.baldev.airline.pricing.client;

import com.baldev.airline.common.payload.response.FlightResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "flight-ops-service")
public interface FlightOwnershipClient {

    @GetMapping("/api/flights/{id}")
    FlightResponse getFlightById(@PathVariable Long id);
}
