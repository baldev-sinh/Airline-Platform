package com.baldev.airline.pricing.controller;


import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.pricing.service.support.FlightOwnershipVerifier;
import com.baldev.airline.pricing.payload.request.FareRulesRequest;
import com.baldev.airline.pricing.payload.response.FareRulesResponse;
import com.baldev.airline.pricing.service.FareRulesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/fare-rules")
@RequiredArgsConstructor
@RestController
public class FareRuleController {

    private final FareRulesService fareRulesService;
    private final FlightOwnershipVerifier ownership;


    @PostMapping
    public ResponseEntity<FareRulesResponse> createFareRule(
            @Valid @RequestBody FareRulesRequest request, CurrentUser user
    ) {
        // The owning airline comes from the verified fare, never from the request body.
        request.setAirlineId(ownership.requireOwnedFare(user, request.getFareId()));
        return ResponseEntity.status(HttpStatus.CREATED)

                .body(fareRulesService.createFareRules(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FareRulesResponse> getFareRulesById(
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(fareRulesService.getFareRulesById(id));
    }

    @GetMapping("/fare/{fareId}")
    public ResponseEntity<FareRulesResponse> getFareRulesByFareId(
            @PathVariable Long fareId) {
        return ResponseEntity.ok(fareRulesService.getFareRulesByFareId(fareId));
    }

    @GetMapping("/airline/{airlineId}")
    public ResponseEntity<List<FareRulesResponse>> getFareRulesByAirlineId(
            @PathVariable Long airlineId) {
        return ResponseEntity.ok(fareRulesService.getFareRulesByAirlineId(airlineId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<FareRulesResponse> updateFareRules(
            @PathVariable Long id,
            @Valid @RequestBody FareRulesRequest request, CurrentUser user) {
        ownership.requireOwnedFare(user, fareRulesService.getFareRulesById(id).getFareId());
        request.setAirlineId(ownership.requireOwnedFare(user, request.getFareId()));
        return ResponseEntity.ok(fareRulesService.updateFareRules(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFareRules(@PathVariable Long id, CurrentUser user) {
        ownership.requireOwnedFare(user, fareRulesService.getFareRulesById(id).getFareId());
        fareRulesService.deleteFareRules(id);
        return ResponseEntity.noContent().build();
    }
}
