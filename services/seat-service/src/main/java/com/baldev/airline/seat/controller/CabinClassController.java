package com.baldev.airline.seat.controller;


import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.seat.service.support.SeatOwnership;
import com.baldev.airline.common.enums.CabinClassType;
import com.baldev.airline.seat.payload.request.CabinClassRequest;
import com.baldev.airline.seat.payload.response.CabinClassResponse;
import com.baldev.airline.seat.service.CabinClassService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cabin-classes")
@RequiredArgsConstructor
public class CabinClassController {

    private final CabinClassService cabinClassService;
    private final SeatOwnership ownership;

    @PostMapping
    public ResponseEntity<CabinClassResponse> createCabinClass(
            @Valid @RequestBody CabinClassRequest cabinClassRequest, CurrentUser user
    ) {
        ownership.requireOwnedAircraft(user, cabinClassRequest.getAircraftId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        cabinClassService.createCabinClass(cabinClassRequest)
                );
    }

    @GetMapping("/{id}")
    public ResponseEntity<CabinClassResponse> getCabinClassById(@PathVariable Long id) {
        return ResponseEntity.ok(cabinClassService.getCabinClassById(id));
    }

    @GetMapping("/aircraft/{id}/name/{cabinClass}")
    public ResponseEntity<CabinClassResponse> getCabinClassByAircraftIdAndName(
            @PathVariable CabinClassType cabinClass,
            @PathVariable Long id) {
        return ResponseEntity.ok(
                cabinClassService.getByAircraftIdAndName(
                        id, cabinClass
                ));
    }

    @GetMapping("/aircraft/{aircraftId}")
    public ResponseEntity<List<CabinClassResponse>> getCabinClassesByAircraftId(
            @PathVariable Long aircraftId) {
        return ResponseEntity.ok(cabinClassService.getCabinClassesByAircraftId(aircraftId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CabinClassResponse> updateCabinClass(
            @PathVariable Long id,
            @Valid @RequestBody CabinClassRequest request, CurrentUser user) {
        ownership.requireOwnedAircraft(user, cabinClassService.getCabinClassById(id).getAircraftId());
        return ResponseEntity.ok(cabinClassService.updateCabinClass(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCabinClass(@PathVariable Long id, CurrentUser user) {
        ownership.requireOwnedAircraft(user, cabinClassService.getCabinClassById(id).getAircraftId());
        cabinClassService.deleteCabinClass(id);
        return ResponseEntity.noContent().build();
    }

}
