package com.baldev.airline.flightops.repository;


import com.baldev.airline.flightops.model.FlightSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FlightScheduleRepository extends JpaRepository<FlightSchedule, Long> {

    List<FlightSchedule> findByFlightAirlineId(Long airlineId);
}
