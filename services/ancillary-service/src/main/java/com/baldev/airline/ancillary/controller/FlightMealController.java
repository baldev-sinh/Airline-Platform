package com.baldev.airline.ancillary.controller;

import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.ancillary.service.MealService;
import com.baldev.airline.ancillary.service.support.AncillaryOwnership;
import com.baldev.airline.ancillary.payload.request.FlightMealRequest;
import com.baldev.airline.ancillary.payload.response.FlightMealResponse;
import com.baldev.airline.ancillary.service.FlightMealService;
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
    private final MealService mealService;
    private final AncillaryOwnership ownership;

    @PostMapping
    public ResponseEntity<FlightMealResponse> createFlightMeal(
            @Valid @RequestBody FlightMealRequest flightMealRequest, CurrentUser user) {
        // Only the airline's own meals, only on the airline's own flights.
        ownership.requireOwnedFlight(user, flightMealRequest.getFlightId());
        ownership.requireAirline(user, mealService.getMealById(flightMealRequest.getMealId()).getAirlineId(),
                "Meal", flightMealRequest.getMealId());
        FlightMealResponse response = flightMealService.createFlightMeal(
                flightMealRequest
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FlightMealResponse> getFlightMealById(@PathVariable Long id) {
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
            @RequestParam Boolean available, CurrentUser user) {
        ownership.requireOwnedFlight(user, flightMealService.getFlightMealById(id).getFlightId());
        return ResponseEntity.ok(flightMealService.updateFlightMealAvailability(id, available));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFlightMeal(@PathVariable Long id, CurrentUser user) {
        ownership.requireOwnedFlight(user, flightMealService.getFlightMealById(id).getFlightId());
        flightMealService.deleteFlightMeal(id);
        return ResponseEntity.noContent().build();
    }

}
