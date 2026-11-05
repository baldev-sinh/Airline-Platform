package com.baldev.airline.pricing.client;

import com.baldev.airline.common.payload.response.AirlineResponse;
import com.baldev.airline.common.web.RequestHeaders;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "airline-core-service")
public interface AirlineClient {

    @GetMapping("/api/airlines/admin")
    AirlineResponse getAirlineByOwner(@RequestHeader(RequestHeaders.USER_ID) Long userId);
}
