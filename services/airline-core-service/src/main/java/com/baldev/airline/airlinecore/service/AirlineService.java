package com.baldev.airline.airlinecore.service;


import com.baldev.airline.common.enums.AirlineStatus;
import com.baldev.airline.common.payload.request.AirlineRequest;
import com.baldev.airline.common.payload.response.AirlineDropdownItem;
import com.baldev.airline.common.payload.response.AirlineResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AirlineService {

    AirlineResponse createAirline(AirlineRequest request, Long ownerId);
    AirlineResponse getAirlineByOwner(Long ownerId) throws Exception;
    AirlineResponse getAirlineById(Long id) throws Exception;
    Page<AirlineResponse> getAllAirlines(Pageable pageable);
    AirlineResponse updateAirline(AirlineRequest request, Long ownerId) throws Exception;
    void deleteAirline(Long id, Long ownerId) throws Exception;

    AirlineResponse changeStatusByAdmin(Long airlineId, AirlineStatus status) throws Exception;

    List<AirlineDropdownItem> getAirlineDropdown();
}
