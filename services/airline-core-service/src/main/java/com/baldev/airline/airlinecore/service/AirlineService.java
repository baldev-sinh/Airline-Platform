package com.baldev.airline.airlinecore.service;


import com.baldev.airline.common.enums.AirlineStatus;
import com.baldev.airline.airlinecore.payload.request.AirlineRequest;
import com.baldev.airline.airlinecore.payload.response.AirlineDropdownItem;
import com.baldev.airline.airlinecore.payload.response.AirlineResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface AirlineService {

    AirlineResponse createAirline(AirlineRequest request, Long ownerId);
    AirlineResponse getAirlineByOwner(Long ownerId);
    AirlineResponse getAirlineById(Long id);
    Page<AirlineResponse> getAllAirlines(Pageable pageable);
    AirlineResponse updateAirline(AirlineRequest request, Long ownerId);
    void deleteAirline(Long id, Long ownerId);

    AirlineResponse changeStatusByAdmin(Long airlineId, AirlineStatus status);

    List<AirlineDropdownItem> getAirlineDropdown();
}
