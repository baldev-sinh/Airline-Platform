package com.baldev.airline.seat.service;


import com.baldev.airline.common.payload.request.SeatMapRequest;
import com.baldev.airline.common.payload.response.SeatMapResponse;

public interface SeatMapService {

    SeatMapResponse createSeatMap(Long userId, SeatMapRequest request);

    SeatMapResponse getSeatMapById(Long id);

    SeatMapResponse getSeatMapByCabinClass(Long cabinId);

    SeatMapResponse updateSeatMap(Long id, SeatMapRequest request);

    void deleteSeatMap(Long id);
}
