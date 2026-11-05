package com.baldev.service;


import com.baldev.payload.request.SeatRequest;
import com.baldev.payload.response.SeatResponse;

import java.util.List;

public interface SeatService {
    void generateSeats(Long seatMapId) throws Exception;

    List<SeatResponse> getAll();

    SeatResponse updateSeats(Long seatId, SeatRequest request);
}
