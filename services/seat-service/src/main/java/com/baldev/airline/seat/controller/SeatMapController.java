package com.baldev.airline.seat.controller;


import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.seat.service.CabinClassService;
import com.baldev.airline.seat.service.support.SeatOwnership;
import com.baldev.airline.seat.payload.request.SeatMapRequest;
import com.baldev.airline.common.web.ApiResponse;
import com.baldev.airline.seat.payload.response.SeatMapResponse;
import com.baldev.airline.seat.service.SeatMapService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/seat-maps")
public class SeatMapController {

    private final SeatMapService seatMapService;
    private final CabinClassService cabinClassService;
    private final SeatOwnership ownership;


    @PostMapping
    public ResponseEntity<SeatMapResponse> createSeatMap(
            @Valid @RequestBody SeatMapRequest seatMapRequest, CurrentUser user
    ) {
        // The seat map is stored under the caller's airline; the cabin it describes must be theirs too.
        ownership.requireOwnedAircraft(user, cabinClassService.getCabinClassById(seatMapRequest.getCabinClassId()).getAircraftId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        seatMapService.createSeatMap(user.id(), seatMapRequest)
                );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SeatMapResponse> getSeatMapById(@PathVariable Long id) {
        return ResponseEntity.ok(seatMapService.getSeatMapById(id));
    }

    @GetMapping("/cabin-class/{cabinClassId}")
    public ResponseEntity<SeatMapResponse> getSeatMapsByCabinClass(
            @PathVariable Long cabinClassId) {
        SeatMapResponse responses = seatMapService.getSeatMapByCabinClass(cabinClassId);
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SeatMapResponse> updateSeatMap(
            @PathVariable Long id,
            @RequestBody SeatMapRequest request, CurrentUser user) {
        ownership.requireAirline(user, seatMapService.getSeatMapById(id).getAirlineId(), "Seat map", id);
        return ResponseEntity.ok(seatMapService.updateSeatMap(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteSeatMap(@PathVariable Long id, CurrentUser user) {
        ownership.requireAirline(user, seatMapService.getSeatMapById(id).getAirlineId(), "Seat map", id);
        seatMapService.deleteSeatMap(id);
        ApiResponse response = new ApiResponse("Seat Map Deleted");
        return ResponseEntity.ok(response);
    }
}
