package com.baldev.airline.ancillary.service;

import com.baldev.airline.common.payload.request.InsuranceCoverageRequest;
import com.baldev.airline.common.payload.response.InsuranceCoverageResponse;

import java.util.List;

public interface InsuranceCoverageService {

    InsuranceCoverageResponse createCoverage(InsuranceCoverageRequest request);

    InsuranceCoverageResponse updateCoverage(Long id, InsuranceCoverageRequest request);

    void deleteCoverage(Long id);

    InsuranceCoverageResponse getCoverage(Long id);

    List<InsuranceCoverageResponse> getCoverageByAncillaryId(Long ancillaryId);

    List<InsuranceCoverageResponse> getActiveCoverageByAncillaryId(Long ancillaryId);

    List<InsuranceCoverageResponse> getAllCoverages();
}
