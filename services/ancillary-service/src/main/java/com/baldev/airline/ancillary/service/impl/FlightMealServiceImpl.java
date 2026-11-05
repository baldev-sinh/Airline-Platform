package com.baldev.airline.ancillary.service.impl;

import java.util.function.Function;
import java.util.Map;
import com.baldev.airline.common.exception.BusinessRuleException;
import com.baldev.airline.common.util.Money;
import com.baldev.airline.common.payload.request.PriceQuoteRequest;
import java.math.BigDecimal;
import com.baldev.airline.common.exception.ConflictException;
import com.baldev.airline.common.exception.ResourceNotFoundException;
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
    public FlightMealResponse createFlightMeal(FlightMealRequest request) {
        Meal meal = mealRepository.findById(request.getMealId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Meal not found")
                );
        if (flightMealRepository.existsByFlightIdAndMealId(request.getFlightId(), meal.getId())) {
            throw new ConflictException("Meal already exists For Flight");
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
    public FlightMealResponse getFlightMealById(Long id) {
        FlightMeal flightMeal = flightMealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("flight meal not found")
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
    public FlightMealResponse updateFlightMeal(Long id, FlightMealRequest request) {
        FlightMeal flightMeal = flightMealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("flight meal not found")
        );

        flightMeal.setFlightId(request.getFlightId());

        if (request.getMealId() != null) {
            Meal meal = mealRepository.findById(request.getMealId())
                    .orElseThrow(() -> new ResourceNotFoundException("Meal not found"));
            flightMeal.setMeal(meal);
        }
        flightMeal.setAvailable(request.getAvailable());
        flightMeal.setPrice(request.getPrice());
        flightMeal.setDisplayOrder(request.getDisplayOrder());
        FlightMeal saved = flightMealRepository.save(flightMeal);
        return FlightMealMapper.toResponse(saved);
    }

    @Override
    public void deleteFlightMeal(Long id) {
        FlightMeal flightMeal = flightMealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("flight meal not found")
        );
        flightMealRepository.delete(flightMeal);
    }

    @Override
    public FlightMealResponse updateFlightMealAvailability(Long id, Boolean availability) {
        FlightMeal flightMeal = flightMealRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("flight meal not found")
        );
        flightMeal.setAvailable(availability);
        FlightMeal updated = flightMealRepository.save(flightMeal);
        return FlightMealMapper.toResponse(updated);
    }

    @Override
    public BigDecimal calculateMealPrice(PriceQuoteRequest request) {
        List<Long> ids = request.getItemIds();
        if (ids == null || ids.isEmpty()) {
            return Money.normalize(BigDecimal.ZERO);
        }
        Map<Long, FlightMeal> byId = flightMealRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(FlightMeal::getId, Function.identity()));

        BigDecimal total = BigDecimal.ZERO;
        for (Long id : ids) {
            FlightMeal meal = byId.get(id);
            if (meal == null || !request.getFlightId().equals(meal.getFlightId())) {
                throw new ResourceNotFoundException("Meal " + id + " is not offered on this flight");
            }
            if (!Boolean.TRUE.equals(meal.getAvailable())) {
                throw new BusinessRuleException("MEAL_UNAVAILABLE", "Meal " + id + " is not available");
            }
            total = total.add(Money.orZero(meal.getPrice()));
        }
        return Money.normalize(total);
    }
}
