package com.baldev.airline.booking.client;

import com.baldev.airline.common.payload.request.PriceQuoteRequest;
import com.baldev.airline.common.payload.response.PriceQuoteResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ancillary-service", path = "/internal/price-quotes")
public interface AncillaryClient {

    @PostMapping("/ancillaries")
    PriceQuoteResponse quoteAncillaries(@RequestBody PriceQuoteRequest request);

    @PostMapping("/meals")
    PriceQuoteResponse quoteMeals(@RequestBody PriceQuoteRequest request);
}
