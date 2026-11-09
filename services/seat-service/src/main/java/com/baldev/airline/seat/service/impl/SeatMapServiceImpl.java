package com.baldev.airline.seat.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;

import com.baldev.airline.seat.client.AirlineClient;
import com.baldev.airline.seat.mapper.SeatMapMapper;
import com.baldev.airline.seat.model.CabinClass;
import com.baldev.airline.seat.model.SeatMap;
import com.baldev.airline.seat.payload.request.SeatMapRequest;
import com.baldev.airline.seat.client.dto.AirlineResponse;
import com.baldev.airline.seat.payload.response.SeatMapResponse;
import com.baldev.airline.seat.repository.CabinClassRepository;
import com.baldev.airline.seat.repository.SeatMapRepository;
import com.baldev.airline.seat.service.SeatMapService;
import com.baldev.airline.seat.service.SeatService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
// Every public method runs in a transaction, so lazy relations can be mapped to responses (open-in-view is off).
@Transactional
public class SeatMapServiceImpl implements SeatMapService {

    private final CabinClassRepository cabinClassRepository;
    private final SeatMapRepository seatMapRepository;
    private final SeatService seatService;
    private final AirlineClient airlineClient;

    @Override
    public SeatMapResponse createSeatMap(Long userId, SeatMapRequest request) {

        AirlineResponse airlineResponse = airlineClient.getCallerAirline();
        CabinClass cabinClass = cabinClassRepository.findById(request.getCabinClassId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("cabin class not found with given id")
                );

        if (seatMapRepository.existsByAirlineIdAndCabinClassIdAndName(
                airlineResponse.getId(), request.getCabinClassId(), request.getName()
        )) {
            throw new ConflictException("cabin class already exists with given name");
        }

        SeatMap seatMap = SeatMapMapper.toEntity(request, cabinClass);
        seatMap.setAirlineId(airlineResponse.getId());
        SeatMap savedSeatMap = seatMapRepository.save(seatMap);

        // generate seats for seat map
        seatService.generateSeats(savedSeatMap.getId());

        return SeatMapMapper.toResponse(savedSeatMap);
    }

    @Override
    public SeatMapResponse getSeatMapById(Long id) {
        SeatMap seatMap = seatMapRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("seat map not found with id")
        );
        return SeatMapMapper.toResponse(seatMap);
    }

    @Override
    public SeatMapResponse getSeatMapByCabinClass(Long cabinId) {
        SeatMap seatMap = seatMapRepository.findByCabinClassId(cabinId);

        return SeatMapMapper.toResponse(seatMap);
    }

    @Override
    public SeatMapResponse updateSeatMap(Long id, SeatMapRequest request) {
        SeatMap seatMap = seatMapRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("seat map not found with id")
        );
        SeatMapMapper.updateEntity(request, seatMap);
        SeatMap updated = seatMapRepository.save(seatMap);
        return SeatMapMapper.toResponse(updated);
    }

    @Override
    public void deleteSeatMap(Long id) {
        SeatMap seatMap = seatMapRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("seat map not found with id")
        );
        seatMapRepository.delete(seatMap);
    }
}
