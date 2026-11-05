package com.baldev.airline.pricing.service;



import com.baldev.airline.common.payload.request.BaggagePolicyRequest;
import com.baldev.airline.common.payload.response.BaggagePolicyResponse;

import java.util.List;

public interface BaggagePolicyService {

    BaggagePolicyResponse createBaggagePolicy(BaggagePolicyRequest request) throws Exception;
    BaggagePolicyResponse getBaggagePolicyById(Long id) throws Exception;
    BaggagePolicyResponse getBaggagePolicyByFareId(Long fareId);
    List<BaggagePolicyResponse> getBaggagePoliciesByAirlineId(Long airlineId);
    BaggagePolicyResponse updateBaggagePolicy(Long id, BaggagePolicyRequest request) throws Exception;
    void deleteBaggagePolicy(Long id);

}
