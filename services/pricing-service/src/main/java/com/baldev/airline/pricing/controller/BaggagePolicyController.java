package com.baldev.airline.pricing.controller;


import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.pricing.service.support.FlightOwnershipVerifier;
import com.baldev.airline.pricing.payload.request.BaggagePolicyRequest;
import com.baldev.airline.pricing.payload.response.BaggagePolicyResponse;
import com.baldev.airline.pricing.service.BaggagePolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/baggage-policies")
@RequiredArgsConstructor
public class BaggagePolicyController {
    private final BaggagePolicyService baggagePolicyService;
    private final FlightOwnershipVerifier ownership;

    @PostMapping
    public ResponseEntity<BaggagePolicyResponse> createBaggagePolicy(
            @Valid @RequestBody BaggagePolicyRequest request, CurrentUser user) {
        Long airlineId = ownership.requireOwnedFare(user, request.getFareId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(baggagePolicyService.createBaggagePolicy(request, airlineId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaggagePolicyResponse> getBaggagePolicyById(
            @PathVariable Long id) {
        return ResponseEntity.ok(baggagePolicyService.getBaggagePolicyById(id));
    }

    @GetMapping("/fare/{fareId}")
    public ResponseEntity<BaggagePolicyResponse> getBaggagePolicyByFareId(
            @PathVariable Long fareId) {
        return ResponseEntity.ok(baggagePolicyService.getBaggagePolicyByFareId(fareId));
    }

    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<List<BaggagePolicyResponse>> getBaggagePoliciesByAirlineId(
            @PathVariable Long airlineId) {
        return ResponseEntity.ok(baggagePolicyService.getBaggagePoliciesByAirlineId(airlineId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaggagePolicyResponse> updateBaggagePolicy(
            @PathVariable Long id,
            @Valid @RequestBody BaggagePolicyRequest request, CurrentUser user) {
        ownership.requireOwnedFare(user, baggagePolicyService.getBaggagePolicyById(id).getFareId());
        if (request.getFareId() != null) {
            ownership.requireOwnedFare(user, request.getFareId());
        }
        return ResponseEntity.ok(baggagePolicyService.updateBaggagePolicy(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBaggagePolicy(@PathVariable Long id, CurrentUser user) {
        ownership.requireOwnedFare(user, baggagePolicyService.getBaggagePolicyById(id).getFareId());
        baggagePolicyService.deleteBaggagePolicy(id);
        return ResponseEntity.noContent().build();
    }
}
