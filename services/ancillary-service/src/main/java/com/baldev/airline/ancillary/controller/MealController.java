package com.baldev.airline.ancillary.controller;

import com.baldev.airline.common.web.CurrentUser;
import com.baldev.airline.ancillary.service.support.AncillaryOwnership;
import com.baldev.airline.ancillary.payload.request.MealRequest;
import com.baldev.airline.ancillary.payload.response.MealResponse;
import com.baldev.airline.ancillary.service.MealService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/meals")
public class MealController {
    private final MealService mealService;
    private final AncillaryOwnership ownership;

    @PostMapping
    public ResponseEntity<MealResponse> createMeal(
            @Valid @RequestBody MealRequest mealRequest,
            CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        mealService.createMeal(user.id(), mealRequest
                        )
                );
    }

    @GetMapping("/airline")
    public ResponseEntity<List<MealResponse>> getMealsByAirlineId(
            CurrentUser user) {
        return ResponseEntity.ok(mealService.getByAirlineId(user.id()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MealResponse> getMealById(@PathVariable Long id) {
        return ResponseEntity.ok(mealService.getMealById(id));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<MealResponse> updateMealAvailability(
            @PathVariable Long id,
            @RequestParam Boolean available, CurrentUser user) {
        ownership.requireAirline(user, mealService.getMealById(id).getAirlineId(), "Meal", id);
        return ResponseEntity.ok(mealService.updateAvailability(id, available));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MealResponse> updateMeal(
            @PathVariable Long id,
            @RequestBody MealRequest request, CurrentUser user) {
        ownership.requireAirline(user, mealService.getMealById(id).getAirlineId(), "Meal", id);
        return ResponseEntity.ok(mealService.updateMeal(user.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeal(@PathVariable Long id, CurrentUser user) {
        ownership.requireAirline(user, mealService.getMealById(id).getAirlineId(), "Meal", id);
        mealService.deleteMeal(id);
        return ResponseEntity.noContent().build();
    }
}
