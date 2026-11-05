package com.baldev.airline.pricing.controller;


import com.baldev.airline.common.payload.request.BaggagePolicyRequest;
import com.baldev.airline.common.payload.response.BaggagePolicyResponse;
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

    @PostMapping
    public ResponseEntity<BaggagePolicyResponse> createBaggagePolicy(
            @Valid  @RequestBody BaggagePolicyRequest request) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(baggagePolicyService.createBaggagePolicy(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BaggagePolicyResponse> getBaggagePolicyById(
            @PathVariable Long id) throws Exception {
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
            @Valid @RequestBody BaggagePolicyRequest request) throws Exception {
        return ResponseEntity.ok(baggagePolicyService.updateBaggagePolicy(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBaggagePolicy(@PathVariable Long id) {
        baggagePolicyService.deleteBaggagePolicy(id);
        return ResponseEntity.noContent().build();
    }
}
