package com.baldev.airline.booking.service.integration;

import com.baldev.airline.booking.client.PricingClient;
import com.baldev.airline.common.payload.response.FareResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class FareIntegrationService {

    private final PricingClient pricingClient;

    public Double calculateFareTotal(Long fareId) {
        FareResponse fareResponse = pricingClient.getFareById(fareId);
        Double baseFare = fareResponse.getBaseFare();
        Double taxesAndFees = fareResponse.getTaxesAndFees()
                != null ? fareResponse.getTaxesAndFees() : 0;
        Double airlineFees = fareResponse.getAirlineFees() != null ? fareResponse.getAirlineFees() : 0;

        return baseFare + taxesAndFees + airlineFees;
    }
}
