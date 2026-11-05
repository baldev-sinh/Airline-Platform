package com.baldev.repository;

import com.baldev.model.FlightMeal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlightMealRepository extends JpaRepository<FlightMeal, Long> {

    List<FlightMeal> findByFlightId(Long id);

    boolean existsByFlightIdAndMealId(Long id, Long mealId);
}
