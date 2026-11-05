package com.baldev.airline.ancillary.service;

import com.baldev.airline.common.payload.request.AncillaryRequest;
import com.baldev.airline.common.payload.response.AncillaryResponse;

import java.util.List;

public interface AncillaryService {

    AncillaryResponse createAncillary(Long userId, AncillaryRequest request);

    AncillaryResponse getById(Long id);

    List<AncillaryResponse> getByAirlineId(Long userId);

    AncillaryResponse updateAncillary(Long id, AncillaryRequest request);

    void deleteAncillary(Long id);
}
