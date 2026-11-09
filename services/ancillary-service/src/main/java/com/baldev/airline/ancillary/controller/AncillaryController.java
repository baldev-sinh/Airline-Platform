package com.baldev.airline.ancillary.controller;


import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.ancillary.service.support.AncillaryOwnership;
import com.baldev.airline.ancillary.payload.request.AncillaryRequest;
import com.baldev.airline.ancillary.payload.response.AncillaryResponse;
import com.baldev.airline.ancillary.service.AncillaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ancillaries")
public class AncillaryController {

    private final AncillaryService ancillaryService;
    private final AncillaryOwnership ownership;


    @PostMapping
    public ResponseEntity<AncillaryResponse> createAncillary(
            @Valid @RequestBody AncillaryRequest ancillaryRequest,
            CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ancillaryService.createAncillary(user.id(), ancillaryRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AncillaryResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ancillaryService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<AncillaryResponse>> getAllByAirlineId(
            CurrentUser user
    ) {
        return ResponseEntity.ok(ancillaryService.getByAirlineId(user.id()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AncillaryResponse> update(
            @PathVariable Long id,
            @RequestBody AncillaryRequest request, CurrentUser user) {
        ownership.requireAirline(user, ancillaryService.getById(id).getAirlineId(), "Ancillary", id);
        return ResponseEntity.ok(ancillaryService.updateAncillary(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, CurrentUser user) {
        ownership.requireAirline(user, ancillaryService.getById(id).getAirlineId(), "Ancillary", id);
        ancillaryService.deleteAncillary(id);
        return ResponseEntity.noContent().build();
    }
}
