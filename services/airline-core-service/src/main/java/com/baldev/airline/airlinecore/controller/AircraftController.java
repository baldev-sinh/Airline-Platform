package com.baldev.airline.airlinecore.controller;


import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.airlinecore.payload.request.AircraftRequest;
import com.baldev.airline.airlinecore.payload.response.AircraftResponse;
import com.baldev.airline.airlinecore.service.AircraftService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/aircrafts")
public class AircraftController {
    private final AircraftService aircraftService;


    @PostMapping
    public ResponseEntity<AircraftResponse> createAircraft(
           @Valid @RequestBody AircraftRequest aircraftRequest,
           CurrentUser user
    ) {
        AircraftResponse aircraft = aircraftService.createAircraft(aircraftRequest,user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                aircraft
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<AircraftResponse> getAircraftById(@PathVariable Long id) {
        return ResponseEntity.ok(aircraftService.getAircraftById(id));
    }

    @GetMapping
    public ResponseEntity<List<AircraftResponse>> listAllAircrafts(
            CurrentUser user) {
        return ResponseEntity.ok(aircraftService.listAllAircraftByOwner(user.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AircraftResponse> updateAircraft(
            @PathVariable Long id,
            @RequestBody AircraftRequest request,
            CurrentUser user) {
        return ResponseEntity.ok(aircraftService.updateAircraft(id, request, user.id()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAircraft(@PathVariable Long id,
                                               CurrentUser user) {
        aircraftService.deleteAircraft(id,user.id());
        return ResponseEntity.noContent().build();
    }
}
