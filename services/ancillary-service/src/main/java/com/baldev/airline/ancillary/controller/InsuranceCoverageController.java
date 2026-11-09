package com.baldev.airline.ancillary.controller;

import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.ancillary.service.AncillaryService;
import com.baldev.airline.ancillary.service.support.AncillaryOwnership;
import com.baldev.airline.ancillary.payload.request.InsuranceCoverageRequest;
import com.baldev.airline.common.web.ApiResponse;
import com.baldev.airline.ancillary.payload.response.InsuranceCoverageResponse;
import com.baldev.airline.ancillary.service.InsuranceCoverageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insurance-coverages")
@RequiredArgsConstructor
public class InsuranceCoverageController {

    private final InsuranceCoverageService insuranceCoverageService;
    private final AncillaryService ancillaryService;
    private final AncillaryOwnership ownership;

    @PostMapping
    public ResponseEntity<InsuranceCoverageResponse> createCoverage(
            @Valid @RequestBody InsuranceCoverageRequest insuranceCoverageRequest, CurrentUser user) {
        requireOwnedAncillary(user, insuranceCoverageRequest.getAncillaryId());
        InsuranceCoverageResponse response = insuranceCoverageService.createCoverage(
                insuranceCoverageRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    public ResponseEntity<InsuranceCoverageResponse> updateCoverage(
            @PathVariable Long id,
            @RequestBody InsuranceCoverageRequest request, CurrentUser user) {
        requireOwnedAncillary(user, insuranceCoverageService.getCoverage(id).getAncillaryId());
        if (request.getAncillaryId() != null) {
            requireOwnedAncillary(user, request.getAncillaryId());
        }
        return ResponseEntity.ok(insuranceCoverageService.updateCoverage(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteCoverage(
            @PathVariable Long id, CurrentUser user) {
        requireOwnedAncillary(user, insuranceCoverageService.getCoverage(id).getAncillaryId());
        insuranceCoverageService.deleteCoverage(id);
        return ResponseEntity.ok(new ApiResponse("Coverage deleted successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InsuranceCoverageResponse> getCoverageById(@PathVariable Long id) {
        return ResponseEntity.ok(insuranceCoverageService.getCoverage(id));
    }

    @GetMapping
    public ResponseEntity<List<InsuranceCoverageResponse>> getAllCoverages() {
        return ResponseEntity.ok(insuranceCoverageService.getAllCoverages());
    }

    @GetMapping("/ancillary/{ancillaryId}")
    public ResponseEntity<List<InsuranceCoverageResponse>> getCoveragesByAncillaryId(
            @PathVariable Long ancillaryId) {
        return ResponseEntity.ok(insuranceCoverageService.getCoverageByAncillaryId(ancillaryId));
    }

    @GetMapping("/ancillary/{ancillaryId}/active")
    public ResponseEntity<List<InsuranceCoverageResponse>> getActiveCoveragesByAncillaryId(
            @PathVariable Long ancillaryId) {
        return ResponseEntity.ok(insuranceCoverageService.getActiveCoverageByAncillaryId(ancillaryId));
    }



    private void requireOwnedAncillary(CurrentUser user, Long ancillaryId) {
        ownership.requireAirline(user, ancillaryService.getById(ancillaryId).getAirlineId(), "Ancillary", ancillaryId);
    }
}
