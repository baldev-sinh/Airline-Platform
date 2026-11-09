package com.baldev.airline.flightops.service.impl;

import com.baldev.airline.common.enums.FlightStatus;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.flightops.payload.request.FlightRequest;
import com.baldev.airline.flightops.payload.response.FlightResponse;
import com.baldev.airline.flightops.mapper.FlightMapper;
import com.baldev.airline.flightops.model.Flight;
import com.baldev.airline.flightops.repository.FlightRepository;
import com.baldev.airline.flightops.service.FlightService;
import com.baldev.airline.flightops.service.support.AirlineOwnership;
import com.baldev.airline.flightops.service.support.FlightResponseAssembler;
import com.baldev.airline.flightops.config.FlightOpsCacheConfig;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class FlightServiceImpl implements FlightService {

    private final FlightRepository flightRepository;
    private final AirlineOwnership airlineOwnership;
    private final FlightResponseAssembler assembler;

    @Override
    @Transactional
    public FlightResponse createFlight(Long userId, FlightRequest request) {
        Long airlineId = airlineOwnership.airlineIdOf(userId);
        if (flightRepository.existsByFlightNumber(request.getFlightNumber())) {
            throw new ConflictException("FLIGHT_NUMBER_TAKEN", "Flight number " + request.getFlightNumber() + " already exists");
        }
        validateRoute(request.getDepartureAirportId(), request.getArrivalAirportId());
        Flight flight = FlightMapper.toEntity(request);
        flight.setAirlineId(airlineId);
        return assembler.toFlightResponse(flightRepository.save(flight));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlightResponse> getFlightsByAirline(Long userId, Long departureAirportId, Long arrivalAirportId,
                                                    Pageable pageable) {
        Long airlineId = airlineOwnership.airlineIdOf(userId);
        Page<Flight> page = flightRepository.findByAirlineId(airlineId, departureAirportId, arrivalAirportId, pageable);
        return new PageImpl<>(assembler.toFlightResponses(page.getContent()), pageable, page.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    // A partial response (a dependency was down) is served but never cached.
    @Cacheable(cacheNames = FlightOpsCacheConfig.FLIGHTS, key = "#id", unless = "#result.partial == true")
    public FlightResponse getFlightById(Long id) {
        return assembler.toFlightResponse(flightRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Flight", id)));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = FlightOpsCacheConfig.FLIGHTS, key = "#id")
    public FlightResponse updateFlight(Long userId, Long id, FlightRequest request) {
        Flight existing = findOwned(userId, id);
        if (request.getFlightNumber() != null
                && flightRepository.existsByFlightNumberAndIdNot(request.getFlightNumber(), id)) {
            throw new ConflictException("FLIGHT_NUMBER_TAKEN", "Flight number " + request.getFlightNumber() + " already exists");
        }
        FlightMapper.updateEntity(request, existing);
        validateRoute(existing.getDepartureAirportId(), existing.getArrivalAirportId());
        return assembler.toFlightResponse(flightRepository.save(existing));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = FlightOpsCacheConfig.FLIGHTS, key = "#id")
    public FlightResponse changeStatus(Long userId, Long id, FlightStatus status) {
        Flight existing = findOwned(userId, id);
        existing.setStatus(status);
        return assembler.toFlightResponse(flightRepository.save(existing));
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = FlightOpsCacheConfig.FLIGHTS, key = "#id")
    public void deleteFlight(Long userId, Long id) {
        flightRepository.delete(findOwned(userId, id));
    }

    private Flight findOwned(Long userId, Long id) {
        Long airlineId = airlineOwnership.airlineIdOf(userId);
        return flightRepository.findByAirlineIdAndId(airlineId, id)
                .orElseThrow(() -> ResourceNotFoundException.of("Flight", id));
    }

    private static void validateRoute(Long departureAirportId, Long arrivalAirportId) {
        if (Objects.equals(departureAirportId, arrivalAirportId)) {
            throw new BusinessRuleException("INVALID_ROUTE", "Departure and arrival airports must differ");
        }
    }
}
