package com.baldev.airline.seat.service;


import com.baldev.airline.seat.payload.request.SeatRequest;
import com.baldev.airline.seat.payload.response.SeatResponse;
import java.util.List;

public interface SeatService {
    void generateSeats(Long seatMapId);

    List<SeatResponse> getAll();

    SeatResponse updateSeats(Long seatId, SeatRequest request);
}
