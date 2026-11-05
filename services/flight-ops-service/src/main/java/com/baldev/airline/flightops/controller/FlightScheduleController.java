package com.baldev.airline.flightops.controller;

import com.baldev.airline.common.payload.request.FlightScheduleRequest;
import com.baldev.airline.common.payload.response.FlightScheduleResponse;
import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.flightops.service.FlightScheduleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flight-schedules")
public class FlightScheduleController {

    private final FlightScheduleService flightScheduleService;

    @PostMapping
    public ResponseEntity<FlightScheduleResponse> createFlightSchedule(CurrentUser user,
                                                                       @Valid @RequestBody FlightScheduleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(flightScheduleService.createFlightSchedule(user.id(), request));
    }

    @GetMapping("/{id}")
    public FlightScheduleResponse getFlightScheduleById(@PathVariable Long id) {
        return flightScheduleService.getFlightScheduleById(id);
    }

    @GetMapping
    public List<FlightScheduleResponse> getFlightSchedules(CurrentUser user) {
        return flightScheduleService.getFlightScheduleByAirline(user.id());
    }

    @PutMapping("/{id}")
    public FlightScheduleResponse updateFlightSchedule(@PathVariable Long id,
                                                       @RequestBody FlightScheduleRequest request,
                                                       CurrentUser user) {
        return flightScheduleService.updateFlightSchedule(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightSchedule(@PathVariable Long id, CurrentUser user) {
        flightScheduleService.deleteFlightSchedule(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
