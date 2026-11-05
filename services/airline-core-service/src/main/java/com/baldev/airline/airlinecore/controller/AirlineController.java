package com.baldev.airline.airlinecore.controller;

import com.baldev.airline.common.exception.ForbiddenException;
import com.baldev.airline.common.web.CurrentUser;

import com.baldev.airline.common.enums.AirlineStatus;
import com.baldev.airline.common.payload.request.AirlineRequest;
import com.baldev.airline.common.payload.response.AirlineDropdownItem;
import com.baldev.airline.common.payload.response.AirlineResponse;
import com.baldev.airline.common.payload.response.ApiResponse;
import com.baldev.airline.airlinecore.service.AirlineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/airlines")
@RequiredArgsConstructor
public class AirlineController {

    private final AirlineService airlineService;

    @PostMapping
    public ResponseEntity<AirlineResponse> createAirline(
            @Valid @RequestBody AirlineRequest airlineRequest,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                airlineService.createAirline(airlineRequest,userId)
        );

    }

    @GetMapping("/admin")
    public ResponseEntity<AirlineResponse> getAirlineByOwner(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(airlineService.getAirlineByOwner(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AirlineResponse> getAirlineById(

            @PathVariable Long id) {
        return ResponseEntity.ok(airlineService.getAirlineById(id));
    }

    @GetMapping
    public ResponseEntity<Page<AirlineResponse>> getAllAirlines(Pageable pageable, CurrentUser user) {
        requireAdmin(user);
        return ResponseEntity.ok(airlineService.getAllAirlines(pageable));
    }

    @GetMapping("/dropdown")
    public ResponseEntity<List<AirlineDropdownItem>> getAirlinesForDropdown() {
        return ResponseEntity.ok(airlineService.getAirlineDropdown());
    }

    @PutMapping
    public ResponseEntity<AirlineResponse> updateAirline(
            @Valid @RequestBody AirlineRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(airlineService.updateAirline(request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteAirline(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {
        airlineService.deleteAirline(id, userId);
        ApiResponse res=new ApiResponse("airline deleted successfully");
        return ResponseEntity.ok(
                res
        );
    }


    @PostMapping("/{id}/approve")
    public ResponseEntity<AirlineResponse> approveAirline(@PathVariable Long id, CurrentUser user) {
        requireAdmin(user);
        return ResponseEntity.ok(airlineService.changeStatusByAdmin(id, AirlineStatus.ACTIVE));
    }

    @PostMapping("/{id}/suspend")
    public ResponseEntity<AirlineResponse> suspendAirline(@PathVariable Long id, CurrentUser user) {
        requireAdmin(user);
        return ResponseEntity.ok(airlineService.changeStatusByAdmin(id, AirlineStatus.INACTIVE));
    }

    @PostMapping("/{id}/ban")
    public ResponseEntity<AirlineResponse> banAirline(@PathVariable Long id, CurrentUser user) {
        requireAdmin(user);
        return ResponseEntity.ok(airlineService.changeStatusByAdmin(id, AirlineStatus.BANNED));
    }


    /** Defense in depth: the gateway already restricts these routes to admins. */
    private static void requireAdmin(CurrentUser user) {
        if (!user.isSystemAdmin()) {
            throw new ForbiddenException("System administrator role required");
        }
    }
}
