package com.baldev.airline.ancillary.controller;

import com.baldev.airline.common.enums.AncillaryType;
import com.baldev.airline.common.payload.request.FlightCabinAncillaryRequest;
import com.baldev.airline.common.payload.response.FlightCabinAncillaryResponse;
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

    @PostMapping
    public ResponseEntity<FlightCabinAncillaryResponse> createFlightCabinAncillary(
            @Valid @RequestBody FlightCabinAncillaryRequest request) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightCabinAncillaryResponse> getById(@PathVariable Long id)
            throws Exception {
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
            @PathVariable AncillaryType type) throws Exception {
        return ResponseEntity.ok(
                service.getByFlightIdAndCabinClassIdAndType(flightId, cabinClassId, type));
    }

    @GetMapping("/flight/{flightId}/cabin/{cabinClassId}/type/{type}/all")
    public ResponseEntity<?> getAllByFlightAndCabinClassAndType(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId,
            @PathVariable AncillaryType type) throws Exception {
        return ResponseEntity.ok(
                service.getAllByFlightIdAndCabinClassIdAndType(flightId, cabinClassId, type));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FlightCabinAncillaryResponse> update(
            @PathVariable Long id,
            @RequestBody FlightCabinAncillaryRequest request)
            throws Exception {
        return ResponseEntity.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws Exception {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/price/total")
    public ResponseEntity<?> calculateAncillariesPrice(
            @RequestBody List<Long> flightCabinAncillaryIds) {
        return ResponseEntity.ok(service.calculateAncillaryPrice(flightCabinAncillaryIds));
    }

}
