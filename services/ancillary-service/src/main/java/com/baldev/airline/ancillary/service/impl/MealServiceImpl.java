package com.baldev.airline.ancillary.service.impl;

import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
import com.baldev.airline.ancillary.client.AirlineClient;
import com.baldev.airline.ancillary.mapper.MealMapper;
import com.baldev.airline.ancillary.model.Meal;
import com.baldev.airline.ancillary.payload.request.MealRequest;
import com.baldev.airline.ancillary.client.dto.AirlineResponse;
import com.baldev.airline.ancillary.payload.response.MealResponse;
import com.baldev.airline.ancillary.repository.MealRepository;
import com.baldev.airline.ancillary.service.MealService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
// Every public method runs in a transaction, so lazy relations can be mapped to responses (open-in-view is off).
@Transactional
public class MealServiceImpl implements MealService {
    private final MealRepository mealRepository;
    private final AirlineClient airlineClient;

    @Override
    public MealResponse createMeal(Long userId, MealRequest request) {

        AirlineResponse airlineResponse = airlineClient.getCallerAirline();

        if (mealRepository.existsByCodeAndAirlineId(request.getCode(), airlineResponse.getId())) {
            throw new ConflictException("meal code already exist");
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
    public MealResponse getMealById(Long id) {
        Meal meal = mealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("meal not found with id")
        );

        return MealMapper.toResponse(meal);
    }

    @Override
    public MealResponse updateMeal(Long userId, Long id, MealRequest request) {
        AirlineResponse airlineResponse = airlineClient.getCallerAirline();
        Meal meal = mealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("meal not found with id")
        );

        if (request.getCode() != null &&
                mealRepository.existsByAirlineIdAndCodeAndIdNot(
                        airlineResponse.getId(), request.getCode(), meal.getId())) {
            throw new ConflictException("meal code already exist");
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
        AirlineResponse airlineResponse = airlineClient.getCallerAirline();
        return mealRepository.findByAirlineId(airlineResponse.getId())
                .stream().map(
                        MealMapper::toResponse
                ).collect(Collectors.toList());
    }

    @Override
    public void deleteMeal(Long id) {
        Meal meal = mealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("meal not found with id")
        );
        mealRepository.delete(meal);
    }

    @Override
    public MealResponse updateAvailability(Long id, Boolean availability) {
        Meal meal = mealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("meal not found with id")
        );
        meal.setAvailable(availability);
        Meal updated = mealRepository.save(meal);
        return MealMapper.toResponse(updated);
    }
}
