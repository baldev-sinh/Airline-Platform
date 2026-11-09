package com.baldev.airline.pricing.service;



import com.baldev.airline.pricing.payload.request.BaggagePolicyRequest;
import com.baldev.airline.pricing.payload.response.BaggagePolicyResponse;
import java.util.List;

public interface BaggagePolicyService {

    /** @param airlineId the airline that owns the fare (verified by the caller) */
    BaggagePolicyResponse createBaggagePolicy(BaggagePolicyRequest request, Long airlineId);
    BaggagePolicyResponse getBaggagePolicyById(Long id);
    BaggagePolicyResponse getBaggagePolicyByFareId(Long fareId);
    List<BaggagePolicyResponse> getBaggagePoliciesByAirlineId(Long airlineId);
    BaggagePolicyResponse updateBaggagePolicy(Long id, BaggagePolicyRequest request);
    void deleteBaggagePolicy(Long id);

}
