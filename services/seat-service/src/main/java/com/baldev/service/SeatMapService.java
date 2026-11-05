package com.baldev.service;


import com.baldev.payload.request.SeatMapRequest;
import com.baldev.payload.response.SeatMapResponse;

public interface SeatMapService {

    SeatMapResponse createSeatMap(Long userId, SeatMapRequest request) throws Exception;

    SeatMapResponse getSeatMapById(Long id) throws Exception;

    SeatMapResponse getSeatMapByCabinClass(Long cabinId);

    SeatMapResponse updateSeatMap(Long id, SeatMapRequest request) throws Exception;

    void deleteSeatMap(Long id) throws Exception;
}
