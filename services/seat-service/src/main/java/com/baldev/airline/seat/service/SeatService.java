package com.baldev.airline.seat.service;


import com.baldev.airline.common.payload.request.SeatRequest;
import com.baldev.airline.common.payload.response.SeatResponse;

import java.util.List;

public interface SeatService {
    void generateSeats(Long seatMapId) throws Exception;

    List<SeatResponse> getAll();

    SeatResponse updateSeats(Long seatId, SeatRequest request);
}
