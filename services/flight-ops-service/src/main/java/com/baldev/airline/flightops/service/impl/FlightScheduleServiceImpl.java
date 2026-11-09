package com.baldev.airline.flightops.service.impl;

import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.flightops.payload.request.FlightScheduleRequest;
import com.baldev.airline.flightops.payload.response.FlightScheduleResponse;
import com.baldev.airline.flightops.mapper.FlightScheduleMapper;
import com.baldev.airline.flightops.model.Flight;
import com.baldev.airline.flightops.model.FlightSchedule;
import com.baldev.airline.flightops.repository.FlightRepository;
import com.baldev.airline.flightops.repository.FlightScheduleRepository;
import com.baldev.airline.flightops.service.FlightInstanceService;
import com.baldev.airline.flightops.service.FlightScheduleService;
import com.baldev.airline.flightops.service.support.AirlineOwnership;
import com.baldev.airline.flightops.service.support.FlightResponseAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightScheduleServiceImpl implements FlightScheduleService {

    private final FlightRepository flightRepository;
    private final FlightScheduleRepository flightScheduleRepository;
    private final FlightInstanceService flightInstanceService;
    private final AirlineOwnership airlineOwnership;
    private final FlightResponseAssembler assembler;

    /** Upper bound on how far ahead one schedule may generate instances (airlines typically sell ~330 days). */
    @Value("${airline.flight-ops.max-schedule-days:366}")
    private long maxScheduleDays;

    @Override
    @Transactional
    public FlightScheduleResponse createFlightSchedule(Long userId, FlightScheduleRequest request) {
        Long airlineId = airlineOwnership.airlineIdOf(userId);
        Flight flight = flightRepository.findByAirlineIdAndId(airlineId, request.getFlightId())
                .orElseThrow(() -> ResourceNotFoundException.of("Flight", request.getFlightId()));
        validate(request.getStartDate(), request.getEndDate(), request.getOperatingDays());

        FlightSchedule saved = flightScheduleRepository.save(FlightScheduleMapper.toEntity(request, flight));

        int created = 0;
        for (LocalDate date = saved.getStartDate(); !date.isAfter(saved.getEndDate()); date = date.plusDays(1)) {
            if (!saved.getOperatingDays().contains(date.getDayOfWeek())) {
                continue;
            }
            LocalDateTime departure = LocalDateTime.of(date, saved.getDepartureTime());
            LocalDateTime arrival = LocalDateTime.of(date, saved.getArrivalTime());
            if (!arrival.isAfter(departure)) {
                // Overnight sector, e.g. dep 23:30 arr 02:10: arrival is on the next calendar day.
                arrival = arrival.plusDays(1);
            }
            flightInstanceService.createInstance(flight, saved.getId(), departure, arrival);
            created++;
        }
        log.info("Created schedule id={} for flight {} with {} instances", saved.getId(), flight.getFlightNumber(), created);
        return assembler.toScheduleResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public FlightScheduleResponse getFlightScheduleById(Long id) {
        return assembler.toScheduleResponse(flightScheduleRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Flight schedule", id)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlightScheduleResponse> getFlightScheduleByAirline(Long userId) {
        Long airlineId = airlineOwnership.airlineIdOf(userId);
        return assembler.toScheduleResponses(flightScheduleRepository.findByFlightAirlineId(airlineId));
    }

    @Override
    @Transactional
    public FlightScheduleResponse updateFlightSchedule(Long userId, Long id, FlightScheduleRequest request) {
        FlightSchedule schedule = findOwned(userId, id);
        FlightScheduleMapper.updateEntity(request, schedule);
        validate(schedule.getStartDate(), schedule.getEndDate(), schedule.getOperatingDays());
        // Note: already-generated instances are not regenerated; change those individually.
        return assembler.toScheduleResponse(flightScheduleRepository.save(schedule));
    }

    @Override
    @Transactional
    public void deleteFlightSchedule(Long userId, Long id) {
        flightScheduleRepository.delete(findOwned(userId, id));
    }

    private FlightSchedule findOwned(Long userId, Long id) {
        Long airlineId = airlineOwnership.airlineIdOf(userId);
        return flightScheduleRepository.findByIdAndFlightAirlineId(id, airlineId)
                .orElseThrow(() -> ResourceNotFoundException.of("Flight schedule", id));
    }

    private void validate(LocalDate start, LocalDate end, List<?> operatingDays) {
        if (start == null || end == null) {
            throw new BusinessRuleException("INVALID_SCHEDULE", "Start and end dates are required");
        }
        if (end.isBefore(start)) {
            throw new BusinessRuleException("INVALID_SCHEDULE", "End date is before start date");
        }
        if (ChronoUnit.DAYS.between(start, end) > maxScheduleDays) {
            throw new BusinessRuleException("INVALID_SCHEDULE", "A schedule may span at most " + maxScheduleDays + " days");
        }
        if (operatingDays == null || operatingDays.isEmpty()) {
            throw new BusinessRuleException("INVALID_SCHEDULE", "At least one operating day is required");
        }
    }
}
