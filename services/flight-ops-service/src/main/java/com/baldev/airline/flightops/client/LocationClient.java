package com.baldev.airline.flightops.client;


import com.baldev.airline.common.payload.response.AirportResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name="location-service")
public interface LocationClient {

    @GetMapping("/api/airports/{id}")
    AirportResponse getAirportById(@PathVariable Long id);
}
