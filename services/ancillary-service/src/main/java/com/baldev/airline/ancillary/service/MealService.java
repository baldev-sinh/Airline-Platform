package com.baldev.airline.ancillary.service;

import com.baldev.airline.ancillary.payload.request.MealRequest;
import com.baldev.airline.ancillary.payload.response.MealResponse;
import java.util.List;

public interface MealService {

    MealResponse createMeal(Long userId, MealRequest request);

    MealResponse getMealById(Long id);

    MealResponse updateMeal(Long userId, Long id, MealRequest request);

    List<MealResponse> getByAirlineId(Long userId);

    void deleteMeal(Long id);

    MealResponse updateAvailability(Long id, Boolean availability);

}
