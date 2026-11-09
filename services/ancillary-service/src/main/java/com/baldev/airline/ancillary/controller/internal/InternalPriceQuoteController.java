package com.baldev.airline.ancillary.controller.internal;

import com.baldev.airline.ancillary.service.FlightCabinAncillaryService;
import com.baldev.airline.ancillary.service.FlightMealService;
import com.baldev.airline.ancillary.payload.request.PriceQuoteRequest;
import com.baldev.airline.ancillary.payload.response.PriceQuoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service pricing endpoints used by booking-service. Everything under {@code /internal} is never
 * routed by the API gateway, so it is unreachable from outside the private network.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/price-quotes")
public class InternalPriceQuoteController {

    private final FlightCabinAncillaryService flightCabinAncillaryService;
    private final FlightMealService flightMealService;

    @PostMapping("/ancillaries")
    public PriceQuoteResponse quoteAncillaries(@Valid @RequestBody PriceQuoteRequest request) {
        return new PriceQuoteResponse(flightCabinAncillaryService.calculateAncillaryPrice(request));
    }

    @PostMapping("/meals")
    public PriceQuoteResponse quoteMeals(@Valid @RequestBody PriceQuoteRequest request) {
        return new PriceQuoteResponse(flightMealService.calculateMealPrice(request));
    }
}
