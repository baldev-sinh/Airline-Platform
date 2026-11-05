package com.baldev.airline.pricing.controller;

import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.pricing.service.support.FlightOwnershipVerifier;
import com.baldev.airline.common.payload.request.FareRequest;
import com.baldev.airline.common.payload.response.FareResponse;
import com.baldev.airline.pricing.service.FareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fares")
public class FareController {

    private final FareService fareService;
    private final FlightOwnershipVerifier ownership;


    @PostMapping
    public ResponseEntity<FareResponse> createFare(
            @Valid @RequestBody FareRequest fareRequest, CurrentUser user) {
        ownership.requireOwnedFlight(user, fareRequest.getFlightId());

        return ResponseEntity.status(HttpStatus.CREATED).body(
                fareService.createFare(fareRequest)
        );

    }

    @GetMapping
    public ResponseEntity<?> getFares() {
        return ResponseEntity.status(HttpStatus.OK)
                .body(fareService.getFares());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FareResponse> getFareById(@PathVariable Long id) {
        return ResponseEntity.ok(fareService.getFareById(id));
    }

    @GetMapping("/flight/{flightId}/cabin-class/{cabinClassId}")
    public ResponseEntity<List<FareResponse>> getFaresByFlightAndCabinClass(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId) {
        return ResponseEntity.ok(fareService.getFaresByFlightIdAndCabinClassId(flightId, cabinClassId));
    }

    @PostMapping("/batch-by-ids")
    public ResponseEntity<Map<Long, FareResponse>> getFaresByIds(@RequestBody List<Long> ids) {
        return ResponseEntity.ok(fareService.getFaresByIds(ids));
    }

    @GetMapping("/lowest/flight/{flightId}/cabin-class/{cabinClassId}")
    ResponseEntity<FareResponse> getLowestFareForFlightAndCabinClass(
            @PathVariable Long flightId,
            @PathVariable Long cabinClassId
    ){
        return ResponseEntity.ok(fareService.getLowestFareForFlightAndCabin(
                flightId,
                cabinClassId
        ));
    }

    @PostMapping("/search")
    public ResponseEntity<Map<Long, FareResponse>> getLowestFarePerFlight(
            @RequestBody List<Long> flightIds,
            @RequestParam Long cabinClassId) {
        Map<Long, FareResponse> res= fareService.getLowestFarePerFlight(flightIds, cabinClassId);
        return ResponseEntity.ok(res);
    }

    @PutMapping("/{id}")
    public ResponseEntity<FareResponse> updateFare(
            @PathVariable Long id,
            @Valid @RequestBody FareRequest request, CurrentUser user) {
        ownership.requireOwnedFlight(user, fareService.getFareById(id).getFlightId());
        ownership.requireOwnedFlight(user, request.getFlightId());
        return ResponseEntity.ok(fareService.updateFare(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFare(@PathVariable Long id, CurrentUser user) {
        ownership.requireOwnedFlight(user, fareService.getFareById(id).getFlightId());
        fareService.deleteFare(id);
        return ResponseEntity.noContent().build();
    }

}
