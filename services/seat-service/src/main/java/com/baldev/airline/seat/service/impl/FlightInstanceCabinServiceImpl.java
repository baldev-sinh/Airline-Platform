package com.baldev.airline.seat.service.impl;

import com.baldev.airline.seat.config.SeatProperties;
import java.math.BigDecimal;
import com.baldev.airline.common.exception.ResourceNotFoundException;

import com.baldev.airline.common.enums.SeatAvailabilityStatus;
import com.baldev.airline.common.enums.SeatType;
import com.baldev.airline.seat.mapper.FlightInstanceCabinMapper;
import com.baldev.airline.seat.model.CabinClass;
import com.baldev.airline.seat.model.FlightInstanceCabin;
import com.baldev.airline.seat.model.SeatInstance;
import com.baldev.airline.seat.model.SeatMap;
import com.baldev.airline.common.payload.request.FlightInstanceCabinRequest;
import com.baldev.airline.common.payload.response.FlightInstanceCabinResponse;
import com.baldev.airline.seat.repository.CabinClassRepository;
import com.baldev.airline.seat.repository.FlightInstanceCabinRepository;
import com.baldev.airline.seat.repository.SeatInstanceRepository;
import com.baldev.airline.seat.repository.SeatMapRepository;
import com.baldev.airline.seat.service.FlightInstanceCabinService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
// Every public method runs in a transaction, so lazy relations can be mapped to responses (open-in-view is off).
@Transactional
public class FlightInstanceCabinServiceImpl implements FlightInstanceCabinService {

    private final CabinClassRepository cabinClassRepository;
    private final SeatMapRepository seatMapRepository;
    private final FlightInstanceCabinRepository flightInstanceCabinRepository;
    private final SeatInstanceRepository seatInstanceRepository;
    private final SeatProperties properties;

    @Override
    public FlightInstanceCabinResponse createFlightInstanceCabin(FlightInstanceCabinRequest request) {
        CabinClass cabinClass = cabinClassRepository.findById(request.getCabinClassId())
                .orElseThrow(() -> new ResourceNotFoundException("Cabin class not found"));

        SeatMap seatMap = seatMapRepository.findByCabinClassId(cabinClass.getId());

        if (seatMap == null) {
            throw new ResourceNotFoundException("Seat Map not found");
        }
        if (seatMap.getSeats() == null || seatMap.getSeats().isEmpty()) {
            throw new ResourceNotFoundException("no seat found in seat map");
        }

        int totalSeats = seatMap.getSeats().size();

        FlightInstanceCabin cabin = FlightInstanceCabin.builder()
                .flightInstanceId(request.getFlightInstanceId())
                .cabinClass(cabinClass)
                .totalSeats(totalSeats)
                .bookedSeats(0)
                .build();

        FlightInstanceCabin savedCabin = flightInstanceCabinRepository.save(cabin);

//      generate seat instances

        List<SeatInstance> seatInstances = seatMap.getSeats().stream()
                .map(
                        seat -> {
                            BigDecimal premiumSuperCharge = getPremiumSuperCharge(
                                    seat.getSeatType(),
                                    properties.windowSurcharge(),
                                    properties.aisleSurcharge()
                            );
                            SeatInstance seatInstance = SeatInstance.builder()
                                    .flightId(request.getFlightId())
                                    .status(SeatAvailabilityStatus.AVAILABLE)
                                    .flightInstanceId(request.getFlightInstanceId())
                                    .flightInstanceCabin(savedCabin)
                                    .seat(seat)
                                    .premiumSupercharge(premiumSuperCharge)
                                    .build();
                            return seatInstance;
                        }
                ).toList();

        seatInstanceRepository.saveAll(seatInstances);
        savedCabin.setSeats(seatInstances);


        return FlightInstanceCabinMapper.toResponse(savedCabin);
    }

    @Override
    public FlightInstanceCabinResponse getFlightInstanceCabinById(Long id) {
        FlightInstanceCabin fic = flightInstanceCabinRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Flight instance cabin not found with id: " + id));
        return FlightInstanceCabinMapper.toResponse(fic);
    }

    @Override
    public Page<FlightInstanceCabinResponse> getByFlightInstanceId(Long id, Pageable pageable) {
        return flightInstanceCabinRepository.findByFlightInstanceId(id, pageable)
                .map(FlightInstanceCabinMapper::toResponse);
    }

    @Override
    public FlightInstanceCabinResponse getByFlightInstanceIdAndCabinClassId(Long flightInstanceId, Long cabinClassId) {
        FlightInstanceCabin cabin = flightInstanceCabinRepository.findByFlightInstanceIdAndCabinClassId(
                flightInstanceId,
                cabinClassId
        );
        return FlightInstanceCabinMapper.toResponse(cabin);
    }

    @Override
    public FlightInstanceCabinResponse updateFlightInstanceCabin(Long id, FlightInstanceCabinRequest request) {
        FlightInstanceCabin fic = flightInstanceCabinRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Flight instance cabin not found with id: " + id));

        if (request.getCabinClassId() != null) {
            CabinClass cabinClass = cabinClassRepository.findById(request.getCabinClassId())
                    .orElseThrow(() -> new ResourceNotFoundException("cabin class not found"));
            fic.setCabinClass(cabinClass);
        }
        FlightInstanceCabin updated = flightInstanceCabinRepository.save(fic);
        return FlightInstanceCabinMapper.toResponse(updated);
    }

    @Override
    public void deleteFlightInstanceCabin(Long id) {

        FlightInstanceCabin fic = flightInstanceCabinRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Flight instance cabin not found with id: " + id));
        flightInstanceCabinRepository.delete(fic);

    }

    private BigDecimal getPremiumSuperCharge(SeatType seatType,
                                         BigDecimal windowSuperCharge,
                                         BigDecimal aisleSuperCharge) {
        if (seatType == null) return BigDecimal.ZERO;

        return switch (seatType) {
            case AISLE -> aisleSuperCharge;
            case WINDOW -> windowSuperCharge;
            default -> BigDecimal.ZERO;
        };
    }
}
