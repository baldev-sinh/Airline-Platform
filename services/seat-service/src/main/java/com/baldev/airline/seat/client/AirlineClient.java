package com.baldev.airline.seat.client;

import com.baldev.airline.seat.client.dto.AircraftResponse;
import com.baldev.airline.seat.client.dto.AirlineResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/admin")
    /** The airline owned by the caller (airline-core reads the owner from the relayed access token). */
    AirlineResponse getCallerAirline();

    @GetMapping("/api/aircrafts/{id}")
    AircraftResponse getAircraftById(@PathVariable Long id);
}
