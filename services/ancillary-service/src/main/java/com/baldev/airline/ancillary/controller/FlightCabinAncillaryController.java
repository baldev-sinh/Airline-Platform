package com.baldev.airline.ancillary.controller;

import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.ancillary.service.AncillaryService;
import com.baldev.airline.ancillary.service.support.AncillaryOwnership;
import com.baldev.airline.common.enums.AncillaryType;
import com.baldev.airline.ancillary.payload.request.FlightCabinAncillaryRequest;
import com.baldev.airline.ancillary.payload.response.FlightCabinAncillaryResponse;
import com.baldev.airline.ancillary.service.FlightCabinAncillaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flight-cabin-ancillaries")
public class FlightCabinAncillaryController {

    private final FlightCabinAncillaryService service;
    private final AncillaryService ancillaryService;
    private final AncillaryOwnership ownership;

    @PostMapping
    public ResponseEntity<FlightCabinAncillaryResponse> createFlightCabinAncillary(
            @Valid @RequestBody FlightCabinAncillaryRequest request, CurrentUser user) {
        ownership.requireOwnedFlight(user, request.getFlightId());
        ownership.requireAirline(user, ancillaryService.getById(request.getAncillaryId()).getAirlineId(),
                "Ancillary", request.getAncillaryId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightCabinAncillaryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/flight/{flightId}/cabin/{cabinClassId}")
    public ResponseEntity<List<FlightCabinAncillaryResponse>> getAllByFlightAndCabinClass(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId) {
        return ResponseEntity.ok(service.getByFlightAndCabinClass(flightId, cabinClassId));
    }

    @GetMapping("/flight/{flightId}/cabin/{cabinClassId}/type/{type}")
    public ResponseEntity<FlightCabinAncillaryResponse> getByFlightAndCabinClassAndType(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId,
            @PathVariable AncillaryType type) {
        return ResponseEntity.ok(
                service.getByFlightIdAndCabinClassIdAndType(flightId, cabinClassId, type));
    }

    @GetMapping("/flight/{flightId}/cabin/{cabinClassId}/type/{type}/all")
    public ResponseEntity<?> getAllByFlightAndCabinClassAndType(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId,
            @PathVariable AncillaryType type) {
        return ResponseEntity.ok(
                service.getAllByFlightIdAndCabinClassIdAndType(flightId, cabinClassId, type));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlightCabinAncillaryResponse> update(
            @PathVariable Long id,
            @RequestBody FlightCabinAncillaryRequest request, CurrentUser user) {
        ownership.requireOwnedFlight(user, service.getById(id).getFlightId());
        if (request.getFlightId() != null) {
            ownership.requireOwnedFlight(user, request.getFlightId());
        }
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, CurrentUser user) {
        ownership.requireOwnedFlight(user, service.getById(id).getFlightId());
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

}
