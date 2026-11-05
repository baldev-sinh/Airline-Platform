package com.baldev.airline.ancillary.repository;

import com.baldev.airline.ancillary.model.FlightMeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlightMealRepository extends JpaRepository<FlightMeal, Long> {

    List<FlightMeal> findByFlightId(Long id);

    boolean existsByFlightIdAndMealId(Long id, Long mealId);
}
