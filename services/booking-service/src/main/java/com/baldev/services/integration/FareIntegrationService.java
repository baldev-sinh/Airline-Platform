package com.baldev.services.integration;

import com.baldev.client.PricingClient;
import com.baldev.payload.response.FareResponse;
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
