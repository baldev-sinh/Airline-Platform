package com.baldev.airline.airlinecore.repository;

import com.baldev.airline.common.enums.AirlineStatus;
import com.baldev.airline.airlinecore.model.Airline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AirlineRepository extends JpaRepository<Airline, Long> {

    Optional<Airline> findByOwnerId(Long ownerId);
    List<Airline> findByStatus(AirlineStatus status);
}
