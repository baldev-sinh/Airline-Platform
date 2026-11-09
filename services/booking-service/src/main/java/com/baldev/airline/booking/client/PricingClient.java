package com.baldev.airline.booking.client;

import com.baldev.airline.booking.client.dto.FareResponse;
import com.baldev.airline.booking.client.dto.FareRulesResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "pricing-service")
public interface PricingClient {

    @GetMapping("/api/fares/{id}")
    FareResponse getFareById(@PathVariable Long id);

    /** 404 (ResourceNotFoundException) when the fare has no rules. */
    @GetMapping("/api/fare-rules/fare/{fareId}")
    FareRulesResponse getFareRulesByFareId(@PathVariable Long fareId);
}
