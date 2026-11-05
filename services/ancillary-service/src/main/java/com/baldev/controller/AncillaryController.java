package com.baldev.controller;


import com.baldev.payload.request.AncillaryRequest;
import com.baldev.payload.response.AncillaryResponse;
import com.baldev.services.AncillaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ancillaries")
public class AncillaryController {

    private final AncillaryService ancillaryService;


    @PostMapping
    public ResponseEntity<AncillaryResponse> createAncillary(
            @Valid @RequestBody AncillaryRequest ancillaryRequest,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(ancillaryService.createAncillary(userId, ancillaryRequest));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AncillaryResponse> getById(@PathVariable Long id)
            throws Exception {
        return ResponseEntity.ok(ancillaryService.getById(id));
    }

    @GetMapping
    public ResponseEntity<List<AncillaryResponse>> getAllByAirlineId(
            @RequestHeader("X-User-Id") Long userId
    ) {
        return ResponseEntity.ok(ancillaryService.getByAirlineId(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AncillaryResponse> update(
            @PathVariable Long id,
            @RequestBody AncillaryRequest request) throws Exception {
        return ResponseEntity.ok(ancillaryService.updateAncillary(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) throws Exception {
        ancillaryService.deleteAncillary(id);
        return ResponseEntity.noContent().build();
    }
}
