package com.baldev.airline.ancillary.controller;

import com.baldev.airline.common.payload.request.MealRequest;
import com.baldev.airline.common.payload.response.MealResponse;
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

    @PostMapping
    public ResponseEntity<MealResponse> createMeal(
            @Valid @RequestBody MealRequest mealRequest,
            @RequestHeader("X-User-Id") Long userId) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        mealService.createMeal(userId, mealRequest
                        )
                );
    }

    @GetMapping("/airline")
    public ResponseEntity<List<MealResponse>> getMealsByAirlineId(
            @RequestHeader("X-User-Id") Long userId) {
        return ResponseEntity.ok(mealService.getByAirlineId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MealResponse> getMealById(@PathVariable Long id)
            throws Exception {
        return ResponseEntity.ok(mealService.getMealById(id));
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<MealResponse> updateMealAvailability(
            @PathVariable Long id,
            @RequestParam Boolean available) throws Exception {
        return ResponseEntity.ok(mealService.updateAvailability(id, available));
    }

    @PutMapping("/{id}")
    public ResponseEntity<MealResponse> updateMeal(
            @PathVariable Long id,
            @RequestBody MealRequest request,
            @RequestHeader("X-User-Id") Long userId) throws Exception {
        return ResponseEntity.ok(mealService.updateMeal(userId, id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMeal(@PathVariable Long id) throws Exception {
        mealService.deleteMeal(id);
        return ResponseEntity.noContent().build();
    }
}
