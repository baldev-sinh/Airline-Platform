package com.baldev.airline.seat.client;

import com.baldev.airline.seat.client.dto.AircraftResponse;
import com.baldev.airline.seat.client.dto.AirlineResponse;
import com.baldev.airline.common.web.RequestHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/admin")
    AirlineResponse getAirlineByOwner(@RequestHeader(RequestHeaders.USER_ID) Long userId);

    @GetMapping("/api/aircrafts/{id}")
    AircraftResponse getAircraftById(@PathVariable Long id);
}
