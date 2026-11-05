package com.baldev.airline.ancillary.service.impl;

import com.baldev.airline.ancillary.mapper.FlightMealMapper;
import com.baldev.airline.ancillary.model.FlightMeal;
import com.baldev.airline.ancillary.model.Meal;
import com.baldev.airline.common.payload.request.FlightMealRequest;
import com.baldev.airline.common.payload.response.FlightMealResponse;
import com.baldev.airline.ancillary.repository.FlightMealRepository;
import com.baldev.airline.ancillary.repository.MealRepository;
import com.baldev.airline.ancillary.service.FlightMealService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlightMealServiceImpl implements FlightMealService {
    private final MealRepository mealRepository;
    private final FlightMealRepository flightMealRepository;

    @Override
    public FlightMealResponse createFlightMeal(FlightMealRequest request) throws Exception {
        Meal meal = mealRepository.findById(request.getMealId())
                .orElseThrow(
                        () -> new Exception("Meal not found")
                );
        if (flightMealRepository.existsByFlightIdAndMealId(request.getFlightId(), meal.getId())) {
            throw new Exception("Meal already exists For Flight");
        }
        FlightMeal flightMeal = FlightMeal.builder()
                .flightId(request.getFlightId())
                .meal(meal)
                .available(request.getAvailable())
                .price(request.getPrice())
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : null)
                .build();

        FlightMeal saved = flightMealRepository.save(flightMeal);
        return FlightMealMapper.toResponse(saved);
    }

    @Override
    public FlightMealResponse getFlightMealById(Long id) throws Exception {
        FlightMeal flightMeal = flightMealRepository.findById(id).orElseThrow(
                () -> new Exception("flight meal not found")
        );
        return FlightMealMapper.toResponse(flightMeal);
    }

    @Override
    public List<FlightMealResponse> getByFlightId(Long flightId) {
        return flightMealRepository.findByFlightId(flightId).stream()
                .map(FlightMealMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<FlightMealResponse> getAllByIds(List<Long> ids) {
        return flightMealRepository.findAllById(ids)
                .stream()
                .map(FlightMealMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public FlightMealResponse updateFlightMeal(Long id, FlightMealRequest request) throws Exception {
        FlightMeal flightMeal = flightMealRepository.findById(id).orElseThrow(
                () -> new Exception("flight meal not found")
        );

        flightMeal.setFlightId(request.getFlightId());

        if (request.getMealId() != null) {
            Meal meal = mealRepository.findById(request.getMealId())
                    .orElseThrow(() -> new Exception("Meal not found"));
            flightMeal.setMeal(meal);
        }
        flightMeal.setAvailable(request.getAvailable());
        flightMeal.setPrice(request.getPrice());
        flightMeal.setDisplayOrder(request.getDisplayOrder());
        FlightMeal saved = flightMealRepository.save(flightMeal);
        return FlightMealMapper.toResponse(saved);
    }

    @Override
    public void deleteFlightMeal(Long id) throws Exception {
        FlightMeal flightMeal = flightMealRepository.findById(id).orElseThrow(
                () -> new Exception("flight meal not found")
        );
        flightMealRepository.delete(flightMeal);
    }

    @Override
    public FlightMealResponse updateFlightMealAvailability(Long id, Boolean availability) throws Exception {
        FlightMeal flightMeal = flightMealRepository.findById(id).orElseThrow(
                () -> new Exception("flight meal not found")
        );
        flightMeal.setAvailable(availability);
        FlightMeal updated = flightMealRepository.save(flightMeal);
        return FlightMealMapper.toResponse(updated);
    }

    @Override
    public Double calculateMealPrice(List<Long> mealIds) {
        List<FlightMeal> meals = flightMealRepository.findAllById(mealIds);
        double price = 0.0;
        for (FlightMeal meal : meals) {
            price += meal.getPrice();
        }
        return price;
    }
}
