package com.baldev.airline.flightops.service.impl;

import com.baldev.airline.common.enums.FlightStatus;
import com.baldev.airline.common.event.FlightInstanceCreatedEvent;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.flightops.payload.request.FlightInstanceRequest;
import com.baldev.airline.flightops.client.dto.AircraftResponse;
import com.baldev.airline.flightops.payload.response.FlightInstanceResponse;
import com.baldev.airline.flightops.client.AirlineClient;
import com.baldev.airline.flightops.mapper.FlightInstanceMapper;
import com.baldev.airline.flightops.model.Flight;
import com.baldev.airline.flightops.model.FlightInstance;
import com.baldev.airline.flightops.repository.FlightInstanceRepository;
import com.baldev.airline.flightops.repository.FlightRepository;
import com.baldev.airline.flightops.service.FlightInstanceService;
import com.baldev.airline.flightops.service.support.AirlineOwnership;
import com.baldev.airline.flightops.service.support.FlightResponseAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class FlightInstanceServiceImpl implements FlightInstanceService {

    private final FlightRepository flightRepository;
    private final FlightInstanceRepository flightInstanceRepository;
    private final AirlineClient airlineClient;
    private final AirlineOwnership airlineOwnership;
    private final FlightResponseAssembler assembler;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public FlightInstanceResponse createFlightInstance(Long userId, FlightInstanceRequest request) {
        Long airlineId = airlineOwnership.airlineIdOf(userId);
        Flight flight = flightRepository.findByAirlineIdAndId(airlineId, request.getFlightId())
                .orElseThrow(() -> ResourceNotFoundException.of("Flight", request.getFlightId()));

        FlightInstance saved = createInstance(flight, request.getScheduleId(),
                request.getDepartureDateTime(), request.getArrivalDateTime());
        if (request.getMinAdvanceBookingDays() != null || request.getMaxAdvanceBookingDays() != null) {
            saved.setMinAdvanceBookingDays(request.getMinAdvanceBookingDays());
            saved.setMaxAdvanceBookingDays(request.getMaxAdvanceBookingDays());
        }
        return assembler.toInstanceResponse(saved);
    }

    @Override
    @Transactional
    public FlightInstance createInstance(Flight flight, Long scheduleId, LocalDateTime departure, LocalDateTime arrival) {
        validateTimes(departure, arrival);
        AircraftResponse aircraft = airlineClient.getAircraftById(flight.getAircraftId());
        if (aircraft.getTotalSeats() == null || aircraft.getTotalSeats() <= 0) {
            throw new BusinessRuleException("AIRCRAFT_HAS_NO_SEATS", "Aircraft " + aircraft.getId() + " has no seats configured");
        }

        FlightInstance instance = FlightInstance.builder()
                .flight(flight)
                .airlineId(flight.getAirlineId())
                .scheduleId(scheduleId)
                .departureAirportId(flight.getDepartureAirportId())
                .arrivalAirportId(flight.getArrivalAirportId())
                .departureDateTime(departure)
                .arrivalDateTime(arrival)
                .totalSeats(aircraft.getTotalSeats())
                .availableSeats(aircraft.getTotalSeats())
                .status(FlightStatus.SCHEDULED)
                .isActive(true)
                .build();
        FlightInstance saved = flightInstanceRepository.save(instance);

        // Delivered to Kafka only after this transaction commits (see FlightInstanceEventProducer).
        eventPublisher.publishEvent(FlightInstanceCreatedEvent.builder()
                .flightInstanceId(saved.getId())
                .flightId(flight.getId())
                .aircraftId(flight.getAircraftId())
                .build());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public FlightInstanceResponse getFlightInstanceById(Long id) {
        FlightInstance instance = flightInstanceRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Flight instance", id));
        return assembler.toInstanceResponse(instance);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<FlightInstanceResponse> getByAirlineId(Long userId, Long departureAirportId, Long arrivalAirportId,
                                                       Long flightId, LocalDate onDate, Pageable pageable) {
        Long airlineId = airlineOwnership.airlineIdOf(userId);
        LocalDateTime start = onDate != null ? onDate.atStartOfDay() : null;
        LocalDateTime end = onDate != null ? onDate.plusDays(1).atStartOfDay() : null;

        Page<FlightInstance> page = flightInstanceRepository.findByAirlineId(
                airlineId, departureAirportId, arrivalAirportId, flightId, start, end, pageable);
        return new PageImpl<>(assembler.toInstanceResponses(page.getContent()), pageable, page.getTotalElements());
    }

    @Override
    @Transactional
    public FlightInstanceResponse updateFlightInstance(Long userId, Long id, FlightInstanceRequest request) {
        FlightInstance existing = findOwned(userId, id);
        if (request.getAvailableSeats() != null) {
            // Inventory is owned by the booking flow; letting clients overwrite it would desync seat counts.
            throw new BusinessRuleException("READ_ONLY_FIELD", "availableSeats cannot be modified directly");
        }
        FlightInstanceMapper.updateEntity(request, existing);
        validateTimes(existing.getDepartureDateTime(), existing.getArrivalDateTime());
        return assembler.toInstanceResponse(flightInstanceRepository.save(existing));
    }

    @Override
    @Transactional
    public void deleteFlightInstance(Long userId, Long id) {
        FlightInstance existing = findOwned(userId, id);
        if (existing.getAvailableSeats() < existing.getTotalSeats()) {
            throw new BusinessRuleException("FLIGHT_HAS_BOOKINGS",
                    "Flight instance has bookings; cancel it instead of deleting");
        }
        flightInstanceRepository.delete(existing);
    }

    private FlightInstance findOwned(Long userId, Long id) {
        Long airlineId = airlineOwnership.airlineIdOf(userId);
        return flightInstanceRepository.findByIdAndAirlineId(id, airlineId)
                .orElseThrow(() -> ResourceNotFoundException.of("Flight instance", id));
    }

    private static void validateTimes(LocalDateTime departure, LocalDateTime arrival) {
        if (departure == null || arrival == null) {
            throw new BusinessRuleException("INVALID_SCHEDULE", "Departure and arrival times are required");
        }
        if (!arrival.isAfter(departure)) {
            throw new BusinessRuleException("INVALID_SCHEDULE", "Arrival must be after departure");
        }
    }
}
