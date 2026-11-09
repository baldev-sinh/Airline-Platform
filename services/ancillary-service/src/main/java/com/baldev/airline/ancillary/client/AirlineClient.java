package com.baldev.airline.ancillary.client;


import com.baldev.airline.ancillary.client.dto.AirlineResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/admin")
    /** The airline owned by the caller (airline-core reads the owner from the relayed access token). */
    AirlineResponse getCallerAirline();
}
