package com.baldev.airline.flightops.client;


import com.baldev.airline.flightops.client.dto.AircraftResponse;
import com.baldev.airline.flightops.client.dto.AirlineResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

//AIRLINE-CORE-SERVICE
@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/{id}")
    AirlineResponse getAirlineById(
            @PathVariable Long id
    );

    @GetMapping("/api/aircrafts/{id}")
    AircraftResponse getAircraftById(@PathVariable("id") Long id);

    @GetMapping("/api/airlines/admin")
    AirlineResponse getAirlineByOwner(
            @RequestHeader("X-User-Id") Long userId);

}
