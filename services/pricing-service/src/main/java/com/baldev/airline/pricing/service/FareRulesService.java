package com.baldev.airline.pricing.service;



import com.baldev.airline.pricing.payload.request.FareRulesRequest;
import com.baldev.airline.pricing.payload.response.FareRulesResponse;
import java.util.List;

public interface FareRulesService {

    FareRulesResponse createFareRules(FareRulesRequest request);
    FareRulesResponse getFareRulesById(Long id);
    FareRulesResponse getFareRulesByFareId(Long fareId);
    List<FareRulesResponse> getFareRulesByAirlineId(Long airlineId);
    FareRulesResponse updateFareRules(Long id, FareRulesRequest request);
    void deleteFareRules(Long id);
}
