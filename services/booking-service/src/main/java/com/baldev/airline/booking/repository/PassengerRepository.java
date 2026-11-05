package com.baldev.airline.booking.repository;

import com.baldev.airline.booking.model.Passenger;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PassengerRepository extends JpaRepository<Passenger, Long> {
}
