package com.baldev.services.impl;

import com.baldev.client.AirlineClient;
import com.baldev.mapper.MealMapper;
import com.baldev.model.Meal;
import com.baldev.payload.request.MealRequest;
import com.baldev.payload.response.AirlineResponse;
import com.baldev.payload.response.MealResponse;
import com.baldev.repository.MealRepository;
import com.baldev.services.MealService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MealServiceImpl implements MealService {
    private final MealRepository mealRepository;
    private final AirlineClient airlineClient;

    @Override
    public MealResponse createMeal(Long userId, MealRequest request) throws Exception {

        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);

        if (mealRepository.existsByCodeAndAirlineId(request.getCode(), airlineResponse.getId())) {
            throw new Exception("meal code already exist");
        }

        Meal meal = Meal.builder()
                .code(request.getCode())
                .name(request.getName())
                .mealType(request.getMealType())
                .dietaryRestriction(request.getDietaryRestriction())
                .ingredients(request.getIngredients())
                .imageUrl(request.getImageUrl())
                .requiresAdvanceBooking(request.getRequiresAdvanceBooking() != null
                        ? request.getRequiresAdvanceBooking() : false)
                .advanceBookingHours(request.getAdvanceBookingHours())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
                .airlineId(airlineResponse.getId())
                .build();
        Meal saved = mealRepository.save(meal);
        return MealMapper.toResponse(saved);
    }

    @Override
    public MealResponse getMealById(Long id) throws Exception {
        Meal meal = mealRepository.findById(id).orElseThrow(
                () -> new Exception("meal not found with id")
        );

        return MealMapper.toResponse(meal);
    }

    @Override
    public MealResponse updateMeal(Long userId, Long id, MealRequest request) throws Exception {
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);
        Meal meal = mealRepository.findById(id).orElseThrow(
                () -> new Exception("meal not found with id")
        );

        if (request.getCode() != null &&
                mealRepository.existsByAirlineIdAndCodeAndIdNot(
                        airlineResponse.getId(), request.getCode(), meal.getId())) {
            throw new Exception("meal code already exist");
        }

        meal.setCode(request.getCode());
        meal.setName(request.getName());
        meal.setMealType(request.getMealType());
        meal.setDietaryRestriction(request.getDietaryRestriction());
        meal.setIngredients(request.getIngredients());
        meal.setImageUrl(request.getImageUrl());
        meal.setRequiresAdvanceBooking(request.getRequiresAdvanceBooking());
        meal.setAdvanceBookingHours(request.getAdvanceBookingHours());
        meal.setDisplayOrder(request.getDisplayOrder());

        Meal updated = mealRepository.save(meal);
        return MealMapper.toResponse(updated);
    }

    @Override
    public List<MealResponse> getByAirlineId(Long userId) {
        AirlineResponse airlineResponse = airlineClient.getAirlineByOwner(userId);
        return mealRepository.findByAirlineId(airlineResponse.getId())
                .stream().map(
                        MealMapper::toResponse
                ).collect(Collectors.toList());
    }

    @Override
    public void deleteMeal(Long id) throws Exception {
        Meal meal = mealRepository.findById(id).orElseThrow(
                () -> new Exception("meal not found with id")
        );
        mealRepository.delete(meal);
    }

    @Override
    public MealResponse updateAvailability(Long id, Boolean availability) throws Exception {
        Meal meal = mealRepository.findById(id).orElseThrow(
                () -> new Exception("meal not found with id")
        );
        meal.setAvailable(availability);
        Meal updated = mealRepository.save(meal);
        return MealMapper.toResponse(updated);
    }
}
