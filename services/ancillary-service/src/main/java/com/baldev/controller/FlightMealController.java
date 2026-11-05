package com.baldev.controller;

import com.baldev.payload.request.FlightMealRequest;
import com.baldev.payload.response.FlightMealResponse;
import com.baldev.services.FlightMealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/flight-meals")
public class FlightMealController {

    private final FlightMealService flightMealService;

    @PostMapping
    public ResponseEntity<FlightMealResponse> createFlightMeal(
            @Valid @RequestBody FlightMealRequest flightMealRequest) throws Exception {
        FlightMealResponse response = flightMealService.createFlightMeal(
                flightMealRequest
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/price/total")
    public ResponseEntity<Double> calculateMealPrice(
            @RequestBody List<Long> requests) {
        double responses = flightMealService.calculateMealPrice(requests);
        return ResponseEntity.status(HttpStatus.OK).body(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightMealResponse> getFlightMealById(@PathVariable Long id)
            throws Exception {
        return ResponseEntity.ok(flightMealService.getFlightMealById(id));
    }

    @GetMapping("/flight/{flightId}")
    public ResponseEntity<List<FlightMealResponse>> getMealsByFlightId(
            @PathVariable Long flightId) {
        return ResponseEntity.ok(flightMealService.getByFlightId(flightId));
    }

    @GetMapping("/all")
    public ResponseEntity<List<FlightMealResponse>> getMealsByIds(
            @RequestParam List<Long> Ids) {
        return ResponseEntity.ok(flightMealService.getAllByIds(Ids));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<FlightMealResponse> updateFlightMealAvailability(
            @PathVariable Long id,
            @RequestParam Boolean available) throws Exception {
        return ResponseEntity.ok(flightMealService.updateFlightMealAvailability(id, available));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightMeal(@PathVariable Long id)
            throws Exception {
        flightMealService.deleteFlightMeal(id);
        return ResponseEntity.noContent().build();
    }

}
