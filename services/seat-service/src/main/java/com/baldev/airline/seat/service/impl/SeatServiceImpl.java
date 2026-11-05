package com.baldev.airline.seat.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;

import com.baldev.airline.common.enums.SeatType;
import com.baldev.airline.seat.mapper.SeatMapper;
import com.baldev.airline.seat.model.Seat;
import com.baldev.airline.seat.model.SeatMap;
import com.baldev.airline.common.payload.request.SeatRequest;
import com.baldev.airline.common.payload.response.SeatResponse;
import com.baldev.airline.seat.repository.SeatMapRepository;
import com.baldev.airline.seat.repository.SeatRepository;
import com.baldev.airline.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeatServiceImpl implements SeatService {
    private final SeatRepository seatRepository;
    private final SeatMapRepository seatMapRepository;

    @Override
    public void generateSeats(Long seatMapId) {
        boolean exists = seatRepository.existsBySeatMapId(seatMapId);
        if (exists) {
            throw new ConflictException("seats already created for seat map");
        }
        SeatMap seatMap = seatMapRepository.findById(seatMapId).orElseThrow(
                () -> new ResourceNotFoundException("seat map not found")
        );

        int leftSeatsPerRow = seatMap.getLeftSeatsPerRow();
        int rightSeatsPerRow = seatMap.getRightSeatsPerRow();
        int rows = seatMap.getTotalRows();
        int seatsPerRow = leftSeatsPerRow + rightSeatsPerRow;

        List<Seat> seats = new ArrayList<>();

        for (int row = 1; row <= rows; row++) {
            for (int col = 0; col < seatsPerRow; col++) {
                String seatNum = row + getSeatLetter(col);
                SeatType type = getSeatType(col, leftSeatsPerRow, rightSeatsPerRow);
                Seat seat = Seat.builder()
                        .seatNumber(seatNum)
                        .seatRow(row)
                        .columnLetter(getSeatLetter(col).charAt(0))
                        .seatType(type)
                        .seatMap(seatMap)
                        .build();

                seats.add(seat);

            }
        }
        seatRepository.saveAll(seats);
    }

    private SeatType getSeatType(int col, int leftSeatsPerRow, int rightSeatsPerRow) {

        int totalSeats = leftSeatsPerRow + rightSeatsPerRow;

//        windows
        if (col == 0 || col == totalSeats - 1) return SeatType.WINDOW;
//        left aisle
        if (col == leftSeatsPerRow - 1) return SeatType.AISLE;
//        right aisle
        if (col == leftSeatsPerRow) return SeatType.AISLE;
        return SeatType.MIDDLE;
    }

    private String getSeatLetter(int col) {
        StringBuilder sb = new StringBuilder();
        while (col >= 0) {
            sb.insert(0, (char) ('A' + (col % 26)));
            col = col / 26 - 1;
        }
        return sb.toString();
    }


    @Override
    public List<SeatResponse> getAll() {
        return seatRepository.findAll()
                .stream().map(SeatMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SeatResponse updateSeats(Long seatId, SeatRequest request) {
        return null;
    }
}
