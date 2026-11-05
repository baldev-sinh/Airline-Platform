package com.baldev.airline.flightops.controller;

import com.baldev.airline.common.enums.FlightStatus;
import com.baldev.airline.common.payload.request.FlightRequest;
import com.baldev.airline.common.payload.response.FlightResponse;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.flightops.service.FlightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
public class FlightController {

    private final FlightService flightService;

    @PostMapping
    public ResponseEntity<FlightResponse> createFlight(@Valid @RequestBody FlightRequest request, CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flightService.createFlight(user.id(), request));
    }

    @GetMapping("/{id}")
    public FlightResponse getFlightById(@PathVariable Long id) {
        return flightService.getFlightById(id);
    }

    @GetMapping("/airline")
    public Page<FlightResponse> getFlightsByAirline(CurrentUser user,
                                                    @RequestParam(required = false) Long departureAirportId,
                                                    @RequestParam(required = false) Long arrivalAirportId,
                                                    Pageable pageable) {
        return flightService.getFlightsByAirline(user.id(), departureAirportId, arrivalAirportId, pageable);
    }

    @PutMapping("/{id}")
    public FlightResponse updateFlight(@PathVariable Long id, @RequestBody FlightRequest request, CurrentUser user) {
        return flightService.updateFlight(user.id(), id, request);
    }

    @PatchMapping("/{id}/status")
    public FlightResponse changeStatus(@PathVariable Long id, @RequestParam FlightStatus status, CurrentUser user) {
        return flightService.changeStatus(user.id(), id, status);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlight(@PathVariable Long id, CurrentUser user) {
        flightService.deleteFlight(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
