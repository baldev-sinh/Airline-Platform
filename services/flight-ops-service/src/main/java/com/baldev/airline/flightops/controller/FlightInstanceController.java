package com.baldev.airline.flightops.controller;

import com.baldev.airline.common.payload.request.FlightInstanceRequest;
import com.baldev.airline.common.payload.response.FlightInstanceResponse;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.flightops.service.FlightInstanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flight-instances")
public class FlightInstanceController {

    private final FlightInstanceService flightInstanceService;

    @PostMapping
    public ResponseEntity<FlightInstanceResponse> createFlightInstance(CurrentUser user,
                                                                       @Valid @RequestBody FlightInstanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flightInstanceService.createFlightInstance(user.id(), request));
    }

    @GetMapping("/{id}")
    public FlightInstanceResponse getFlightInstanceById(@PathVariable Long id) {
        return flightInstanceService.getFlightInstanceById(id);
    }

    @GetMapping
    public Page<FlightInstanceResponse> getByAirline(
            CurrentUser user,
            @RequestParam(required = false) Long departureAirportId,
            @RequestParam(required = false) Long arrivalAirportId,
            @RequestParam(required = false) Long flightId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate,
            Pageable pageable) {
        return flightInstanceService.getByAirlineId(user.id(), departureAirportId, arrivalAirportId, flightId,
                onDate, pageable);
    }

    @PutMapping("/{id}")
    public FlightInstanceResponse updateFlightInstance(@PathVariable Long id,
                                                       @RequestBody FlightInstanceRequest request,
                                                       CurrentUser user) {
        return flightInstanceService.updateFlightInstance(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightInstance(@PathVariable Long id, CurrentUser user) {
        flightInstanceService.deleteFlightInstance(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
